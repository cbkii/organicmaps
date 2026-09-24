# InCar screenshot and TS18 qualification bundle

This playbook separates deterministic repository checks from physical TS18 qualification. A green build or emulator/static check is not physical-device proof.

## Prerequisites

- Use the TESTING APK built from the exact PR head being qualified and record its commit SHA/provenance.
- Confirm the target is the intended TS18 before any install or lifecycle action. The current project baseline is Android 10 / API 29 unless current device evidence disproves it.
- Keep the normal Topway launcher/reverse-camera/recovery path available. These checks must not alter MCU/CAN, firmware, panel or vehicle services.
- Install/launch through the normal project TESTING procedure. The capture tools below are read-only: they do not install, grant permissions, change settings/properties or force lifecycle state.
- Do not operate Termux or the head unit while driving. Prepare/capture moving states only with a passenger or in a controlled safe test.

## TS18 Termux collector — preferred physical evidence path

Copy `android/tools/ts18_termux_in_car_ux_capture.sh` to Termux-private storage (for example `$HOME/bin/`) and keep it executable only by the Termux user. The script records ordinary Termux identity first, then uses ordinary Magisk `su` only for bounded read-only Android probes. It deliberately does not use `su -M`, change SELinux, write Android settings/properties, alter packages, or dump last-known precise coordinates.
