# FM Transmitter Frontend for rpitx

Android app that provides a user-friendly frontend for [rpitx](https://github.com/F5OEO/rpitx) - turning your Raspberry Pi into a software-defined radio transmitter.

## Features

- **SSH Connection** to Raspberry Pi (password authentication, key-based auth coming soon)
- **Multiple transmission modes**:
    - Audio files (FM/RDS broadcast)
    - Images (SSTV transmission)
    - Text messages (Morse/RTTY)
- **Live audio recording** from device microphone
- **File transfer** via SCP over SSH
- **One-tap transmission** with rpitx commands

## Requirements

- **Raspberry Pi** with [rpitx installed](https://github.com/F5OEO/rpitx#installation)
- **Android phone** (API 21+ recommended)
- **SSH access** enabled on Pi (`sudo systemctl enable ssh`)
- **Network connectivity** between phone and Pi (same WiFi)

## Quick Start

1. **Install rpitx** on your Raspberry Pi:
   ```bash
   git clone https://github.com/F5OEO/rpitx.git
   cd rpitx
   ./install.sh
   sudo reboot
   ```

2. **Connect your Android phone** to the same network as Pi

3. **Open app** → Enter Pi's IP address and SSH password

4. **Select transmission mode** and send audio/images/text!

## Supported rpitx Modes

| Mode | Command Used | File Type |
|------|--------------|-----------|
| FM Audio | `pifm` | WAV/MP3 |
| RDS | `pifmrds` | WAV |
| SSTV | `pisstv` | PNG/BMP |
| Morse | `pimorse` | Text |

## Screenshots





## Troubleshooting

**"Connection failed"**: Verify Pi IP, SSH enabled, and firewall allows port 22

**"rpitx not found"**: Run `which pifm` on Pi - ensure install.sh completed successfully

**No audio transmission**: Check Pi's `/boot/config.txt` has `gpu_freq=250` and `force_turbo=1`

## Future Plans

- [ ] SSH key authentication
- [ ] Custom rpitx parameters
- [ ] Background service mode

## License

MIT License - feel free to fork and contribute!