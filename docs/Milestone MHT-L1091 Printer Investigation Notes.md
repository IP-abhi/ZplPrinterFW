# Milestone MHT-L1091 Printer: Investigation Notes

*Compiled from a Linux debugging session, 24 October 2026*

## 1. Summary

The Milestone MHT-L1091 is a Bluetooth/USB direct-thermal shipping label printer. The hardware reports itself as made by **Rongta**. It speaks **ZPL** (Zebra Programming Language) at **203 DPI**. It silently ignores TSPL and ESC/POS, which explains the original symptom: apps and drivers that send those languages report print successful (the OS only confirms the bytes were delivered) while the printer never acts on them.

Raw ZPL written to `/dev/usb/lp0` on Linux prints correctly. The remaining open issue is that a full-page shipping-label PDF printed only partly, which looks like a size or alignment problem (section 6).

## 2. Device identification

| Item | Value |
| --- | --- |
| USB vendor:product | `0fe6:8800` (lsusb shows ICS Advent LabelPrinter; 0fe6 is a generic chip vendor ID reused by many clones) |
| Manufacturer string | RONGTA |
| Product string | LabelPrinter |
| Serial number | MHTL1091560800551 |
| Kernel driver | `usblp`, reported as USB Bidirectional printer (protocol 2) |
| Device node | `/dev/usb/lp0`, owner root, group lp, mode 660 |
| Reply to `~HI` | `LabelPrinter,V1.11,8,0KB` |
| Reading of that reply | model string, firmware V1.11, 8 dots/mm (= 203 DPI), 0 KB memory (my interpretation of the Zebra reply format) |
| Label width (retailer listing) | 0.98 in to 4.37 in |
| Mobile app (retailer listing) | Label Expert, iOS and Android, over Bluetooth |
| Print method | Direct thermal, no ribbon |

The printer re-enumerates on the USB bus a few seconds after plug-in (`usblp0: removed`, then re-added). Something else, probably CUPS, briefly claims it. Stop CUPS while testing: `sudo systemctl stop cups.socket cups.service`.

## 3. Findings

### 3.1 What was tried and what happened

| Test | Result |
| --- | --- |
| Self-test (hold the feed button while powering on) | Prints a page of printer details, so mechanism, head and sensor work |
| TSPL queries (`~!T`, `SELFTEST`) | No reply, no print |
| TSPL status query (ESC ! ?) | Always returns one byte `0x00`; not meaningful |
| TSPL jobs (SIZE, GAP, TEXT, BOX, PRINT) with gap, continuous, black-mark and AUTODETECT variants | Nothing happens |
| ESC/POS init plus text | Nothing happens |
| Minimal ZPL (`^XA ... ^XZ`) | A label fed |
| ZPL with `^PW ^LL ^MD`, a box and text, using default media, `^MNN`, `^MNY` and `^MNM` | All four printed correctly (box plus HELLO in two font sizes) |
| `~HI` | Replies with the identity string above |
| `~HS` (host status) | Read fails with errno 5 (Input/output error); treat as unsupported and skip it |

### 3.2 Conclusions

- The command language is **ZPL**. The Rongta driver catalogue also lists ZPL variants alongside TSPL for several models.
- The earlier TSPL hypothesis came from a write-up on the sibling MHT-L1081 and does not hold for this unit.
- The media mode (`^MN`) made no visible difference on the labels used, so the default is fine.
- Because the printer ignores unknown languages without error, any status the OS reports is meaningless as proof of printing.

## 4. Minimal working ZPL

```
^XA
^PW812
^LL1218
^MD30
^PR2
^FO20,20^GB772,1178,6^FS
^FO60,60^A0N,80,80^FDHELLO^FS
^XZ
```

Send it as raw bytes to the device. On Linux this is a plain write to `/dev/usb/lp0` (needs root or membership of the `lp` group). In fish, `sudo printf ... > file` does not work because the redirect runs as your user; use Python or pipe to `sudo tee`.

Key numbers at 203 DPI: a 4x6 in label is 101.6 x 152.4 mm, which is **812 x 1218 dots**. `^PW` is print width in dots, `^LL` is label length in dots, `^MD` is darkness, `^PR` is print speed.

## 5. Printing a PDF or image

Pipeline used:

1. Render the PDF page at 203 DPI (`pdftoppm -r 203`).
2. Convert to greyscale, crop the surrounding whitespace, rotate landscape to portrait, shrink to fit 812 x 1218 if larger, centre on a white canvas.
3. Threshold to 1-bit (black pixel = 1) and pack as bytes.
4. Wrap in a ZPL graphic field and send.

Graphic field format: `^GFA,<total bytes>,<total bytes>,<bytes per row>,<hex data>`, with bytes per row = (width + 7) // 8 (102 for 812 dots).

```python
def to_zpl(img, W=812, H=1218):
    bw = img.point(lambda p: 255 if p < 160 else 0).convert('1')
    data = bw.tobytes()
    bpr = (W + 7) // 8
    n = len(data)
    return (b'^XA^PW%d^LL%d^MD30^PR2^FO0,0^GFA,%d,%d,%d,' % (W, H, n, n, bpr)
            + data.hex().upper().encode() + b'^FS^XZ')
```

Dependencies: `poppler-utils` (for `pdftoppm`) and Pillow. The generated ZPL was saved to `out.zpl` for inspection.

## 6. Open issue: partial print of a shipping-label PDF

The test graphic fits 812 x 1218 dots, but a real PDF printed only partly. Likely causes, roughly in order of probability:

1. ** ****Label length mismatch.** `^LL1218` may not match the real label. Measure the roll and compute the length in dots (mm x 8). If the stock is not exactly 4x6, change `^PW` and `^LL`, and size the canvas to match.
2. **Print width limit.** The printhead may be narrower or wider than 812 dots. Print a full-width box at several widths to find the edge.
3. **Offset or origin.** Try `^LH0,0` and a non-zero `^LT` (label top) or `^PO`/`^PM` settings, and adjust `^FO` on the graphic.
4. **Sensor not calibrated for this stock.** Run `~JC` once, alone, and let the labels feed.
5. **Cropping or scaling in the script.** The crop assumes the label is the only content on the page, and a landscape page may be rotated the wrong way. Print the `out.zpl` image to check.
6. **Large single graphic.** A cheap firmware may truncate big `^GF` payloads. Try sending in chunks, or use the compressed form.

Diagnostic ideas: print a full-border box and a ruler of tick marks to see exactly where the output clips; save the rendered PNG before conversion and compare it against the printed result; preview the ZPL in an online ZPL viewer such as Labelary to separate script errors from printer behaviour.

## 7. Notes for building software

**Layers.** Keep rendering (PDF to image), encoding (image to ZPL) and transport (write bytes to the printer) separate, so the same ZPL can go over USB or Bluetooth.

**USB transport on Linux.** Writing to `/dev/usb/lp0` is enough. For access without sudo, add yourself to the `lp` group or add a udev rule matching idVendor 0fe6 and idProduct 8800. `pyusb` is the alternative if the `usblp` node is unavailable (it would need the kernel driver detached).

**Reading replies.** Open the node read-write and use `select` before reading. `~HI` works. Other queries may stall the endpoint, so catch `OSError` on reads.

**Bluetooth (not yet tested).** The same ZPL bytes should work if the Bluetooth link just carries raw data. First find out whether it is Classic (serial port profile, RFCOMM) or BLE. Tools: `bluetoothctl`, `btmon`, Python `bleak`. For BLE, look for a writable GATT characteristic and write in small chunks.

**Windows.** A Zebra ZPL or generic text-only driver pointed at the printer's port may work. Writing raw data through the spooler (RAW datatype) with `pywin32` is the code route.

**Capturing a known-good app.** On Android, enable the Bluetooth HCI snoop log and open it in Wireshark. On Linux, `usbmon` with Wireshark captures USB traffic. This shows exactly what the Label Expert app sends.

## 8. Not yet verified

- Contents of the self-test page (language setting, media type, label size, darkness). Worth transcribing in full.
- Whether the labels are gap, continuous or black-mark stock, and their exact size.
- Which ZPL commands the firmware supports (fonts, barcodes, `^GF` limits, compression).
- Bluetooth transport type and protocol.
- Whether the Windows driver or the Label Expert app sends ZPL or something else.
- Usable USB endpoint details (`lsusb -v -d 0fe6:8800`).

## 9. References

- Zebra ZPL II Programming Guide (command reference for `^XA`, `^PW`, `^LL`, `^MD`, `^GF`, `~HI`, `~JC` and the rest).
- Rongta printer driver list on the BarTender Seagull drivers page (shows TSPL and ZPL variants for Rongta models).
- Rongta RP422 label printer manual on ManualsLib (self-test and label learning procedure for a related model).
- TSC TSPL/TSPL2 Programming Manual (not applicable to this unit, but useful if a TSPL sibling such as the MHT-L1081 is involved).
- HackMD note: MHT-L1081 setup on Fedora (the sibling that appears to use TSPL).
- Retailer listing for the MHT-L1091 on Flipkart (label width range, Bluetooth app).

## Appendix: raw send helper

```python
import os, select, time

DEV = '/dev/usb/lp0'

def send(data: bytes, wait=1.0) -> bytes:
    fd = os.open(DEV, os.O_RDWR)
    try:
        os.write(fd, data)
        out, end = b'', time.time() + wait
        while time.time() < end:
            r, _, _ = select.select([fd], [], [], 0.2)
            if r:
                try:
                    out += os.read(fd, 4096)
                except OSError:
                    break
        return out
    finally:
        os.close(fd)

print(send(b'~HI'))   # expect LabelPrinter,V1.11,8,0KB
```
