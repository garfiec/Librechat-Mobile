#!/usr/bin/env python3
"""Write the seeder's document asset: town-snack-policy.pdf, the one-page PDF the
whiskers-charter chat attaches. Standard library only, so the asset rebuilds on any machine.

    scripts/demo-seed/make-assets.py              # into scripts/demo-seed/assets/
    scripts/demo-seed/make-assets.py --out DIR

Photos (avatar.png, melted-bar.jpg) aren't made here; generate or take them and put them in the
same directory.
"""

import argparse
from pathlib import Path

LINES = [
    ("Helvetica-Bold", 18, "Town of Puddleby-by-the-Sea"),
    ("Helvetica-Bold", 14, "Municipal Snack Policy, Ordinance No. 42"),
    ("Helvetica", 10, "Adopted by the Town Council. Signed by Mayor Whiskers."),
    ("Helvetica", 11, ""),
    ("Helvetica-Bold", 12, "Section 1. Purpose"),
    ("Helvetica", 11, "To keep the town hall snack drawer stocked, fair, and free of surprises."),
    ("Helvetica", 11, ""),
    ("Helvetica-Bold", 12, "Section 2. Approved snacks"),
    ("Helvetica", 11, "a. Fruit, crackers, and one (1) fish-shaped biscuit per resident per day."),
    ("Helvetica", 11, "b. Chocolate is permitted in moderation, defined as one bar per meeting."),
    ("Helvetica", 11, ""),
    ("Helvetica-Bold", 12, "Section 3. Automated purchasing"),
    ("Helvetica", 11, "a. AI agents may restock snacks within a weekly budget of 20 shells."),
    ("Helvetica", 11, "b. Any single order of more than five Dubai chocolate bars requires a"),
    ("Helvetica", 11, "   signed waiver from a human resident and a short apology from the agent."),
    ("Helvetica", 11, "c. Agents may not describe chocolate as a \"pipeline optimization.\""),
    ("Helvetica", 11, ""),
    ("Helvetica-Bold", 12, "Section 4. Pistachio cream"),
    ("Helvetica", 11, "Pistachio cream is a controlled substance during council meetings and may"),
    ("Helvetica", 11, "only be served on the first Friday of each month."),
    ("Helvetica", 11, ""),
    ("Helvetica-Bold", 12, "Section 5. Enforcement"),
    ("Helvetica", 11, "Violations are reviewed by the Snack Committee, which meets after lunch."),
    ("Helvetica", 11, "Penalties: one week without the good biscuits."),
]


def text_pdf(lines) -> bytes:
    """A single US Letter page of left-aligned lines in the two standard Helvetica faces, which
    every PDF reader has built in, so nothing is embedded."""

    def esc(t):
        return t.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    ops = ["BT", "1 0 0 1 64 760 Tm"]
    for i, (font, size, text) in enumerate(lines):
        if i:
            ops.append(f"0 -{size + 8} Td")
        face = "F2" if font.endswith("Bold") else "F1"
        ops.append(f"/{face} {size} Tf ({esc(text)}) Tj")
    ops.append("ET")
    stream = "\n".join(ops).encode()

    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
        b"/Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>",
    ]
    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for number, body in enumerate(objects, 1):
        offsets.append(len(out))
        out += f"{number} 0 obj\n".encode() + body + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    for offset in offsets:
        out += f"{offset:010d} 00000 n \n".encode()
    out += (f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\n"
            f"startxref\n{xref}\n%%EOF\n").encode()
    return bytes(out)


def main():
    p = argparse.ArgumentParser(description="Write the seeder's document asset.")
    p.add_argument("--out", default=str(Path(__file__).resolve().parent / "assets"))
    target = Path(p.parse_args().out)
    target.mkdir(parents=True, exist_ok=True)
    path = target / "town-snack-policy.pdf"
    data = text_pdf(LINES)
    path.write_bytes(data)
    print(f"wrote {path} ({len(data)} bytes)")


if __name__ == "__main__":
    main()
