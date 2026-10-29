#include <stdbool.h>
#include <stdint.h>

#include "rom_probe.h"
#include "xsdk/gpu.h"
#include "xsdk/kernel.h"
#include "xsdk/log.h"
#include "xsdk/present.h"
#include "xsdk/text.h"

#define ROM_PATH "game:\\sonic1.bin"

enum {
    FRONT_BUFFERS = 2,
    SWAP_INTERVAL = 1,
    FRAME_TIMEOUT_MS = 5000,
    BACKGROUND = 0xFF071426,
    WHITE = 0xFFFFFFFF,
    CYAN = 0xFF55DDEE,
    GREEN = 0xFF70E090,
    YELLOW = 0xFFFFD166,
    RED = 0xFFFF7777
};

static void draw_screen(const RomProbeResult *rom, uint32_t width, uint32_t height,
    uint32_t frame_address, bool details)
{
    xsdk_gpu_set_target(width, height);
    xsdk_text_draw(56, 42, 3, CYAN, "SONIC THE HEDGEHOG");
    xsdk_text_draw(58, 112, 2, WHITE, "MEGA DRIVE - XBOX 360 NATIVE PORT LAB");
    xsdk_text_draw(58, 174, 2, WHITE, "PHASE 0: XEX BOOT + ORIGINAL ROM CHECK");

    if (rom->status == ROM_PROBE_REV00_OK) {
        xsdk_text_draw(58, 254, 2, GREEN, "ROM STATUS: REV00 VERIFIED");
        xsdk_text_draw(58, 296, 1, WHITE, "SIZE: 512 KiB   HEADER: SEGA MEGA DRIVE");
        xsdk_text_draw(58, 326, 1, WHITE, "SERIAL: GM 00001009-00   CHECKSUM: 264A");
    } else if (rom->status == ROM_PROBE_MISSING) {
        xsdk_text_draw(58, 254, 2, YELLOW, "ROM STATUS: FILE NOT FOUND");
        xsdk_text_draw(58, 296, 1, WHITE, "COPY THE EXTRACTED ROM AS sonic1.bin");
        xsdk_text_draw(58, 326, 1, WHITE, "PLACE IT BESIDE THIS XEX");
    } else {
        xsdk_text_draw(58, 254, 2, RED, "ROM STATUS: INVALID OR UNSUPPORTED");
        xsdk_text_draw(58, 296, 1, WHITE, rom->message);
    }

    if (details) {
        xsdk_text_draw(58, 402, 1, CYAN, "THIS BUILD IS A NATIVE XEX BOOT/ROM PROBE.");
        xsdk_text_draw(58, 432, 1, WHITE, "THE PLAYABLE SONIC GAME ENGINE IS NOT PORTED YET.");
        xsdk_text_draw(58, 462, 1, WHITE, "NO MEGA DRIVE CPU EMULATOR IS INCLUDED.");
    } else {
        xsdk_text_draw(58, 420, 1, WHITE, "A: DETAILS     BACK: EXIT");
        xsdk_text_draw(58, 456, 1, WHITE, "RUNS LOCALLY; NO NETWORK CONNECTION IS USED.");
    }

    xsdk_gpu_resolve(frame_address, width, height, BACKGROUND);
}

int main(void)
{
    xsdk_log_write("sonic_x360: native Xbox 360 boot");
    const RomProbeResult rom = rom_probe_file(ROM_PATH);
    xsdk_log_write("sonic_x360: ROM probe: %s", rom.message);

    NTSTATUS status = xsdk_gpu_open();
    if (!NT_SUCCESS(status)) {
        xsdk_log_write("sonic_x360: xsdk_gpu_open failed: 0x%08X", (unsigned)status);
        return 1;
    }

    status = xsdk_text_open();
    if (!NT_SUCCESS(status)) {
        xsdk_log_write("sonic_x360: xsdk_text_open failed: 0x%08X", (unsigned)status);
        (void)xsdk_gpu_close();
        return 1;
    }

    uint32_t width = 0;
    uint32_t height = 0;
    status = xsdk_present_open(XSDK_DISPLAY_720P, FRONT_BUFFERS, SWAP_INTERVAL, &width, &height);
    if (!NT_SUCCESS(status)) {
        xsdk_log_write("sonic_x360: xsdk_present_open failed: 0x%08X", (unsigned)status);
        xsdk_text_close();
        (void)xsdk_gpu_close();
        return 1;
    }

    bool details = false;
    uint16_t previous_buttons = 0;
    for (;;) {
        INPUT_STATE input = {0};
        const uint32_t result = XamInputGetState(0, 0, &input);
        if (result != ERROR_SUCCESS)
            input = (INPUT_STATE){0};

        const uint16_t pressed = (uint16_t)(input.gamepad.buttons & (uint16_t)~previous_buttons);
        previous_buttons = input.gamepad.buttons;
        if ((input.gamepad.buttons & INPUT_GAMEPAD_BACK) != 0)
            break;
        if ((pressed & INPUT_GAMEPAD_A) != 0)
            details = !details;

        uint32_t frame_address = 0;
        status = xsdk_present_begin(&frame_address, FRAME_TIMEOUT_MS);
        if (!NT_SUCCESS(status)) {
            xsdk_log_write("sonic_x360: xsdk_present_begin failed: 0x%08X", (unsigned)status);
            break;
        }
        draw_screen(&rom, width, height, frame_address, details);
        xsdk_present_end();
    }

    xsdk_present_close();
    xsdk_text_close();
    status = xsdk_gpu_close();
    if (!NT_SUCCESS(status))
        xsdk_log_write("sonic_x360: xsdk_gpu_close failed: 0x%08X", (unsigned)status);
    xsdk_log_write("sonic_x360: exited cleanly");
    return 0;
}
