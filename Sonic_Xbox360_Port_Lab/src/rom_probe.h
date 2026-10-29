#ifndef SONIC_ROM_PROBE_H
#define SONIC_ROM_PROBE_H

#include <stddef.h>

typedef enum {
    ROM_PROBE_MISSING = 0,
    ROM_PROBE_INVALID,
    ROM_PROBE_REV00_OK
} RomProbeStatus;

typedef struct {
    RomProbeStatus status;
    size_t file_size;
    unsigned checksum;
    char serial[15];
    char message[96];
} RomProbeResult;

RomProbeResult rom_probe_file(const char *path);

#endif
