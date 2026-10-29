#include "rom_probe.h"

#include <stdio.h>
#include <string.h>

enum {
    SONIC_ROM_BYTES = 0x80000,
    HEADER_BYTES = 0x200,
    CONSOLE_NAME_OFFSET = 0x100,
    SERIAL_OFFSET = 0x180,
    CHECKSUM_OFFSET = 0x18E,
    ROM_END_OFFSET = 0x1A4
};

static unsigned read_be16(const unsigned char *p)
{
    return ((unsigned)p[0] << 8) | (unsigned)p[1];
}

static unsigned read_be32(const unsigned char *p)
{
    return ((unsigned)p[0] << 24) | ((unsigned)p[1] << 16) |
        ((unsigned)p[2] << 8) | (unsigned)p[3];
}

static RomProbeResult make_result(RomProbeStatus status, size_t size,
    unsigned checksum, const char *serial, const char *message)
{
    RomProbeResult out = {0};
    out.status = status;
    out.file_size = size;
    out.checksum = checksum;
    if (serial != NULL)
        (void)snprintf(out.serial, sizeof(out.serial), "%s", serial);
    (void)snprintf(out.message, sizeof(out.message), "%s", message);
    return out;
}

RomProbeResult rom_probe_file(const char *path)
{
    unsigned char header[HEADER_BYTES];
    FILE *file = fopen(path, "rb");
    if (file == NULL)
        return make_result(ROM_PROBE_MISSING, 0, 0, NULL,
            "ROM nao encontrada. Coloque sonic1.bin ao lado do XEX.");

    if (fseek(file, 0, SEEK_END) != 0) {
        (void)fclose(file);
        return make_result(ROM_PROBE_INVALID, 0, 0, NULL, "Falha ao consultar o tamanho da ROM.");
    }
    const long end = ftell(file);
    if (end < 0 || fseek(file, 0, SEEK_SET) != 0) {
        (void)fclose(file);
        return make_result(ROM_PROBE_INVALID, 0, 0, NULL, "Falha ao posicionar a leitura da ROM.");
    }

    const size_t file_size = (size_t)end;
    if (file_size < sizeof(header) || fread(header, 1, sizeof(header), file) != sizeof(header)) {
        (void)fclose(file);
        return make_result(ROM_PROBE_INVALID, file_size, 0, NULL, "Arquivo pequeno demais ou leitura incompleta.");
    }
    (void)fclose(file);

    if (file_size != SONIC_ROM_BYTES)
        return make_result(ROM_PROBE_INVALID, file_size, read_be16(header + CHECKSUM_OFFSET), NULL,
            "Tamanho inesperado: esta etapa espera a ROM de 512 KiB.");

    if (memcmp(header + CONSOLE_NAME_OFFSET, "SEGA MEGA DRIVE ", 16) != 0)
        return make_result(ROM_PROBE_INVALID, file_size, read_be16(header + CHECKSUM_OFFSET), NULL,
            "Cabecalho SEGA MEGA DRIVE nao encontrado.");

    char serial[15];
    memcpy(serial, header + SERIAL_OFFSET, 14);
    serial[14] = '\0';
    const unsigned checksum = read_be16(header + CHECKSUM_OFFSET);
    const unsigned rom_end = read_be32(header + ROM_END_OFFSET);

    if (memcmp(serial, "GM 00001009-00", 14) != 0 || checksum != 0x264A || rom_end != 0x0007FFFF)
        return make_result(ROM_PROBE_INVALID, file_size, checksum, serial,
            "Cabecalho encontrado, mas nao corresponde ao alvo Sonic 1 REV00.");

    return make_result(ROM_PROBE_REV00_OK, file_size, checksum, serial,
        "Sonic the Hedgehog REV00 reconhecido; arquivo original intacto.");
}
