# NearbyTransfer / EasyTransfer

This is a mini cross-platform app to allow data transmission between any two supported devices without a network connection. It is currently done by [optical transfer](https://github.com/bashalarmistalt/decimen-optical-transfer/), which means it is safe to share files even on a plane.

This project contains a Kotlin Multiplatform port of the [decimen optical transfer core](https://github.com/bashalarmistalt/decimen-optical-transfer/). The port is freely for other developers to make use of it under the [license](#License).

Supported devices:
- Android phones and tablets
- iOS phones and tablets
- macOS
- Windows
- Linux

To receive data, your device must have a camera.

To send data, your device must have a display.

## Limitation

1. In my testing, the highest transfer rate is about 55 KB/s.
2. The camera must be able to focus on the animating QR code, or nothing could be scanned.
3. You may have to tweak different options in both receiver and sender in order to transfer data steadily.

## Bug Reporting

If you encounter scanning issue, first check with the [official demo of the upstream project](https://github.com/bashalarmistalt/decimen-optical-transfer/). If you cannot scan even with the upstream project demo, report the bug there; otherwise, report to this repository.

For non-scanning issue, just report to this repository.

## Privacy Policy

Check [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

## License

The license of the optical module is owned by the [upstream project](https://github.com/bashalarmistalt/decimen-optical-transfer/), which is snapshot to be [MIT license](decimen-optical-transfer-kmp/LICENSE).

For the rest of this project, the license is [Apache 2.0](LICENSE.txt).

## For Developers

If you are interested in building or modifying the app yourself, check [this note](DEVELOPER_NOTE.md) to get started.

## Roadmap

It is planned to add a bluetooth functionality to facilitate file sharing.
