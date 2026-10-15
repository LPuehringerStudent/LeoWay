"""Build the specification PDF from Markdown; no network or student data needed.

Run: python docs/tools/build_spec.py
Dependency: python -m pip install -r docs/tools/requirements.txt

Use case, activity and architecture diagrams and the screen sketches are drawn from
the Markdown blocks themselves. Only the domain model is drawn by hand below.
"""
from pathlib import Path
from functools import partial
from xml.sax.saxutils import escape
import os
import re

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.utils import simpleSplit
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.platypus import Flowable, PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

DOCS = Path(__file__).resolve().parents[1]
SOURCE = DOCS / 'LeoWay-System-Specification.md'
OUTPUT = SOURCE.with_suffix('.pdf')
WIDTH = A4[0] - 108
INK = colors.HexColor('#1f1e1d')
GREY = colors.HexColor('#686660')
RULE = colors.HexColor('#deddd8')
PALE = colors.HexColor('#f3f3f0')

# One family throughout, including diagrams and page numbers.
font_dir = Path(os.environ.get('LEOWAY_FONT_DIR', str(Path(os.environ.get('WINDIR', 'C:/Windows')) / 'Fonts')))
font_files = {
    'TimesNewRoman': 'times.ttf', 'TimesNewRoman-Bold': 'timesbd.ttf',
    'TimesNewRoman-Italic': 'timesi.ttf', 'TimesNewRoman-BoldItalic': 'timesbi.ttf',
}
if not all((font_dir / file).is_file() for file in font_files.values()):
    raise FileNotFoundError('Times New Roman is required. Set LEOWAY_FONT_DIR to a folder containing times.ttf, timesbd.ttf, timesi.ttf and timesbi.ttf.')
for name, file in font_files.items():
    pdfmetrics.registerFont(TTFont(name, str(font_dir / file)))
body, bold, italic = 'TimesNewRoman', 'TimesNewRoman-Bold', 'TimesNewRoman-Italic'
pdfmetrics.registerFontFamily(body, normal=body, bold=bold,
    italic=italic, boldItalic='TimesNewRoman-BoldItalic')
STYLES = {
    'title': ParagraphStyle('title', fontName=bold, fontSize=20, leading=26, textColor=INK, spaceAfter=10),
    'h2': ParagraphStyle('h2', fontName=bold, fontSize=16, leading=21, textColor=INK, spaceBefore=12, spaceAfter=6, keepWithNext=True),
    'h3': ParagraphStyle('h3', fontName=bold, fontSize=13, leading=17, textColor=INK, spaceBefore=10, spaceAfter=5, keepWithNext=True),
    'h4': ParagraphStyle('h4', fontName=bold, fontSize=12, leading=16, textColor=INK, spaceBefore=10, spaceAfter=4, keepWithNext=True),
    'body': ParagraphStyle('body', fontName=body, fontSize=12, leading=16, textColor=INK, spaceAfter=6),
    'small': ParagraphStyle('small', fontName=body, fontSize=8.5, leading=11.5, textColor=GREY, spaceAfter=5),
    'cell': ParagraphStyle('cell', fontName=body, fontSize=9.5, leading=12.5, textColor=INK),
    'head': ParagraphStyle('head', fontName=bold, fontSize=9.5, leading=12.5, textColor=INK),
}
# First column widths by table header; the last column takes the rest.
COLUMNS = {
    'Test': [118], 'Term': [104], 'Characteristic': [108], 'Goal': [120], 'Partner': [118],
    'Quantity': [128, 140], 'ID': [42],
}
COLUMNS_3 = {'ID': [42, 210]}

def inline(text):
    text = escape(text)
    text = re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', text)
    text = re.sub(r'`([^`]+)`', r'<i>\1</i>', text)
    return re.sub(r'\[([^\]]+)\]\(([^)]+)\)', r'<link href="\2" color="#454545"><u>\1</u></link>', text)

def paragraph(text, style='body'):
    return Paragraph(inline(text), STYLES[style])

def make_table(lines):
    rows = [[part.strip() for part in line.strip().strip('|').split('|')] for line in lines]
    rows.pop(1)  # Markdown separator.
    header = rows[0][0]
    if header == 'Characteristic':
        return characteristic_grid(rows[1:])
    fixed = (COLUMNS_3.get(header) if len(rows[0]) == 3 else None) or COLUMNS.get(header, [42])
    fixed = fixed[:len(rows[0]) - 1]
    widths = fixed + [WIDTH - sum(fixed)]
    data = [[paragraph(c, 'head' if i == 0 else 'cell') for c in row] for i, row in enumerate(rows)]
    result = Table(data, colWidths=widths, repeatRows=1, hAlign='LEFT')
    result.setStyle(TableStyle([
        ('VALIGN',(0,0),(-1,-1),'TOP'), ('BACKGROUND',(0,0),(-1,0),PALE),
        ('LEFTPADDING',(0,0),(-1,-1),7), ('RIGHTPADDING',(0,0),(-1,-1),7),
        ('TOPPADDING',(0,0),(-1,-1),3), ('BOTTOMPADDING',(0,0),(-1,-1),3),
        ('LINEBELOW',(0,0),(-1,-1),0.4,RULE),
    ]))
    return result

def characteristic_grid(rows):
    """Use case characteristics as label/value pairs, two per line."""
    pairs = [[paragraph(label, 'head'), paragraph(value, 'cell')] for label, value in rows]
    data = [pairs[i] + (pairs[i+1] if i+1 < len(pairs) else ['', '']) for i in range(0, len(pairs), 2)]
    label = 70
    result = Table(data, colWidths=[label, WIDTH/2-label, label, WIDTH/2-label], hAlign='LEFT')
    result.setStyle(TableStyle([
        ('VALIGN',(0,0),(-1,-1),'TOP'), ('BACKGROUND',(0,0),(0,-1),PALE), ('BACKGROUND',(2,0),(2,-1),PALE),
        ('LEFTPADDING',(0,0),(-1,-1),5), ('RIGHTPADDING',(0,0),(-1,-1),5),
        ('TOPPADDING',(0,0),(-1,-1),3), ('BOTTOMPADDING',(0,0),(-1,-1),3),
        ('LINEBELOW',(0,0),(-1,-1),0.4,RULE), ('LINEABOVE',(0,0),(-1,0),0.4,RULE),
    ]))
    return result

NODE = re.compile(r'^(\w+)(\(\((.*)\)\)|\(\[(.*)\]\)|\[\((.*)\)\]|\[(.*)\]|\{(.*)\})?$')
ARROW = re.compile(r'\s*(--\s*(\w+)\s*-->|-\.->|-->)\s*')

def parse_flowchart(lines):
    """Parse a small Mermaid flowchart subset: chains of nodes joined by -->, -.-> or -- label -->."""
    nodes, order, edges = {}, [], []
    for line in lines:
        line = line.strip()
        if not line or line.startswith(('%%', 'flowchart')):
            continue
        parts = ARROW.split(line)
        # split() yields: node, arrow, label, node, arrow, label, node ...
        tokens = parts[0::3]
        arrows = list(zip(parts[1::3], parts[2::3]))
        ids = []
        for token in tokens:
            m = NODE.match(token.strip())
            if not m:
                raise ValueError(f'Cannot read diagram node: {token!r}')
            node, shape = m.group(1), m.group(2) or ''
            if node not in nodes:
                nodes[node] = (node, 'box'); order.append(node)
            if shape:
                kind = ('circle' if shape.startswith('((') else 'oval' if shape.startswith('([') else
                        'database' if shape.startswith('[(') else 'decision' if shape.startswith('{') else 'box')
                label = next(g for g in m.groups()[2:] if g is not None)
                nodes[node] = (label, kind)
            ids.append(node)
        for (src, dst), (arrow, label) in zip(zip(ids, ids[1:]), arrows):
            edges.append((src, dst, label or '', arrow.strip() == '-.->'))
    return nodes, order, edges

class Diagram(Flowable):
    """Vector drawings for the Markdown diagram and sketch blocks."""
    def __init__(self, kind, block):
        super().__init__()
        self.kind = kind
        self.width = WIDTH
        self.block = block
        getattr(self, 'layout_' + kind.replace('-', '_'))()

    # Drawing helpers
    def box(self, x, y, w, h, lines, size=8.5):
        c = self.canv
        c.setFillColor(PALE); c.setStrokeColor(RULE)
        c.roundRect(x, y, w, h, 4, fill=1, stroke=1)
        self.text_lines(x+w/2, y+h/2, lines, size)

    def text_lines(self, cx, cy, lines, size=8.5, font=None):
        c = self.canv
        c.setFillColor(INK); c.setFont(font or body, size)
        step = size + 1.5
        for i, line in enumerate(lines):
            c.drawCentredString(cx, cy+(len(lines)-1)*step/2-i*step-size*0.35, line)

    def head(self, x2, y2, dx, dy):
        c = self.canv
        length = (dx*dx + dy*dy) ** 0.5 or 1
        ux, uy = dx/length, dy/length
        c.line(x2, y2, x2-4*ux+2*uy, y2-4*uy-2*ux); c.line(x2, y2, x2-4*ux-2*uy, y2-4*uy+2*ux)

    def arrow(self, x1, y1, x2, y2, label='', dashed=False):
        c = self.canv
        c.setStrokeColor(GREY); c.setFillColor(GREY); c.setLineWidth(0.65)
        c.setDash(2, 2) if dashed else c.setDash()
        c.line(x1, y1, x2, y2)
        c.setDash()
        self.head(x2, y2, x2-x1, y2-y1)
        if label:
            c.setFont(body, 7); c.drawCentredString((x1+x2)/2, (y1+y2)/2+3, label)

    # Use case diagram: primary actors left, external systems right, use cases in the system boundary.
    def layout_use_case(self):
        self.nodes, order, self.edges = parse_flowchart(self.block)
        self.cases = [n for n in order if self.nodes[n][1] == 'oval']
        self.left = [n for n in order if n not in self.cases and any(s == n for s, *_ in self.edges)]
        self.right = [n for n in order if n not in self.cases and n not in self.left]
        self.height = max(len(self.cases)*28 + 18, 54*max(len(self.left), len(self.right)) + 6)

    def actor_rows(self, actors, case_y):
        rows = []
        for actor in actors:
            linked = [case_y[s if d == actor else d] for s, d, *_ in self.edges if actor in (s, d)]
            rows.append(sum(linked)/len(linked))
        for i in range(1, len(rows)):  # Keep stick figures apart.
            rows[i] = min(rows[i], rows[i-1]-54)
        shift = max(0, 40 - rows[-1]) if rows else 0
        return [min(r+shift, self.height-18) for r in rows]

    def figure(self, cx, cy, name):
        c = self.canv
        c.setStrokeColor(INK); c.setLineWidth(0.8)
        c.circle(cx, cy+12, 4.5)
        c.line(cx, cy+7.5, cx, cy-4); c.line(cx-7, cy+3, cx+7, cy+3)
        c.line(cx, cy-4, cx-6, cy-12); c.line(cx, cy-4, cx+6, cy-12)
        c.setFillColor(INK); c.setFont(body, 8); c.drawCentredString(cx, cy-22, name)

    def draw_use_case(self):
        c = self.canv
        bx, bw = 105, WIDTH - 210
        c.setStrokeColor(RULE); c.setLineWidth(0.8)
        c.rect(bx, 1, bw, self.height-2)
        c.setFillColor(GREY); c.setFont(body, 7); c.drawString(bx+5, self.height-10, 'LeoWay')
        top = self.height/2 + (len(self.cases)-1)*14
        case_y = {n: top - i*28 for i, n in enumerate(self.cases)}
        ow, ox = bw - 70, bx + 35
        sides = [(n, 45, y) for n, y in zip(self.left, self.actor_rows(self.left, case_y))]
        sides += [(n, WIDTH-45, y) for n, y in zip(self.right, self.actor_rows(self.right, case_y))]
        place = {n: (x, y) for n, x, y in sides}
        for src, dst, label, dashed in self.edges:
            actor, case = (src, dst) if src in place else (dst, src)
            x, y = place[actor]
            c.setStrokeColor(GREY); c.setLineWidth(0.65)
            c.setDash(2, 2) if dashed else c.setDash()
            c.line(x+12 if x < bx else x-12, y+3, ox if x < bx else ox+ow, case_y[case])
            c.setDash()
        for n, y in case_y.items():
            c.setFillColor(PALE); c.setStrokeColor(GREY); c.setLineWidth(0.65)
            c.ellipse(ox, y-10, ox+ow, y+10, fill=1, stroke=1)
            self.text_lines(ox+ow/2, y, [self.nodes[n][0]], 8.5)
        for n, x, y in sides:
            self.figure(x, y, self.nodes[n][0])

    # Activity diagram: the main path runs left to right; side branches run in a lane below.
    def layout_activity(self):
        self.nodes, order, self.edges = parse_flowchart(self.block)
        out = {}
        for src, dst, label, _ in self.edges:
            out.setdefault(src, []).append((dst, label))
        main, node = [], order[0]
        while node and node not in main:
            main.append(node)
            choices = out.get(node, [])
            node = next((d for d, l in choices if l == 'yes'), choices[0][0] if choices else None)
        self.lane = {n: 0 for n in main}
        changed = True
        while changed:
            changed = False
            for src, dst, *_ in self.edges:
                if src in self.lane and dst not in self.lane:
                    self.lane[dst] = 1; changed = True
        missing = [n for n in order if n not in self.lane]
        if missing:
            raise ValueError(f'Activity nodes not reachable: {missing}')
        size, gap = 8, 16
        pad = lambda n: 24 if self.nodes[n][1] == 'decision' else 12
        natural = {n: pdfmetrics.stringWidth(self.nodes[n][0], body, size) + pad(n) for n in order if self.nodes[n][1] != 'circle'}
        boxes = [n for n in main if n in natural]
        room = WIDTH - gap*(len(main)-1) - 12*(len(main)-len(boxes))
        least = min(48, room / len(boxes))
        scale = 1.0
        while scale > 0.05 and sum(max(least, natural[n]*scale) for n in boxes) > room:
            scale -= 0.01
        slot = {n: (12 if n not in natural else max(least, natural[n]*scale) if self.lane[n] == 0 else min(natural[n], 130)) for n in order}
        self.wrapped = {n: simpleSplit(self.nodes[n][0], body, size, slot[n] - pad(n)) for n in natural}
        self.size = size
        line_h = lambda n: len(self.wrapped[n])*(size+1.5) + 6 if n in natural else 12
        heights = [max([line_h(n) for n in order if self.lane[n] == lane] or [0]) for lane in (0, 1)]
        self.height = heights[0] + (heights[1] + 16 if heights[1] else 0) + 4
        lane_y = [self.height - 2 - heights[0]/2, self.height - 2 - heights[0] - 16 - heights[1]/2]
        self.geo, x = {}, 0
        for n in main:
            self.geo[n] = [x + slot[n]/2, lane_y[0], self.half(n, slot[n]), line_h(n)/2]
            x += slot[n] + gap
        changed = True
        while changed:  # Place each side node under its main predecessor, or right of its side predecessor.
            changed = False
            for src, dst, *_ in self.edges:
                if dst not in self.geo and src in self.geo:
                    hw = self.half(dst, slot[dst])
                    sx, _, shw, _ = self.geo[src]
                    cx = sx if self.lane[src] == 0 else sx + shw + gap + hw
                    self.geo[dst] = [min(cx, WIDTH - hw - 1), lane_y[1], hw, line_h(dst)/2]
                    changed = True

    def half(self, n, slot):
        if n not in self.wrapped:
            return 6
        width = max(pdfmetrics.stringWidth(l, body, self.size) for l in self.wrapped[n])
        return min(slot, width + (24 if self.nodes[n][1] == 'decision' else 12)) / 2

    def draw_activity(self):
        c = self.canv
        geo = self.geo
        for src, dst, label, dashed in self.edges:
            (sx, sy, sw, sh), (dx, dy, dw, dh) = geo[src], geo[dst]
            c.setStrokeColor(GREY); c.setFillColor(GREY); c.setLineWidth(0.65)
            if self.lane[src] == self.lane[dst]:
                self.arrow(sx+sw, sy, dx-dw, dy)
                if label:
                    c.setFont(body, 6.5); c.drawCentredString((sx+sw+dx-dw)/2, sy+3, label)
            elif self.lane[dst] == 1:  # Down into the side lane.
                c.line(sx, sy-sh, sx, (sy-sh+dy+dh)/2); c.line(sx, (sy-sh+dy+dh)/2, dx, (sy-sh+dy+dh)/2)
                c.line(dx, (sy-sh+dy+dh)/2, dx, dy+dh); self.head(dx, dy+dh, 0, -1)
                if label:
                    c.setFont(body, 6.5); c.drawString(sx+3, sy-sh-7, label)
            else:  # Back up to the main path, left or right of the side node.
                edge = sx-sw if dx < sx else sx+sw
                c.line(edge, sy, dx, sy); c.line(dx, sy, dx, dy-dh); self.head(dx, dy-dh, 0, 1)
        for n, (cx, cy, hw, hh) in geo.items():
            label, kind = self.nodes[n]
            c.setStrokeColor(GREY); c.setLineWidth(0.65)
            if kind == 'circle':
                if label == 'start':
                    c.setFillColor(INK); c.circle(cx, cy, 5, fill=1, stroke=0)
                else:
                    c.setStrokeColor(INK); c.setFillColor(colors.white)
                    c.circle(cx, cy, 6, fill=1, stroke=1)
                    c.setFillColor(INK); c.circle(cx, cy, 3.5, fill=1, stroke=0)
                continue
            if kind == 'decision':
                path = c.beginPath()
                path.moveTo(cx-hw, cy); path.lineTo(cx-hw+8, cy+hh); path.lineTo(cx+hw-8, cy+hh)
                path.lineTo(cx+hw, cy); path.lineTo(cx+hw-8, cy-hh); path.lineTo(cx-hw+8, cy-hh); path.close()
                c.setFillColor(colors.white); c.drawPath(path, fill=1, stroke=1)
            else:
                c.setFillColor(PALE); c.roundRect(cx-hw, cy-hh, 2*hw, 2*hh, 5, fill=1, stroke=1)
            self.text_lines(cx, cy, self.wrapped[n], self.size)

    # Architecture: layered left to right by the longest path from the clients.
    def layout_architecture(self):
        self.nodes, order, self.edges = parse_flowchart(self.block)
        depth = {n: 0 for n in order}
        for _ in order:
            for src, dst, *_ in self.edges:
                depth[dst] = max(depth[dst], depth[src] + 1)
        for n in order:  # Pull pure sources next to their targets, e.g. the trainer beside the backend.
            targets = [depth[d] for s, d, *_ in self.edges if s == n]
            if targets and not any(d == n for _, d, *_ in self.edges):
                depth[n] = min(targets) - 1
        self.layers = [[n for n in order if depth[n] == d] for d in range(max(depth.values()) + 1)]
        self.height = max(len(layer) for layer in self.layers) * 30 + 4

    def draw_architecture(self):
        gap = (WIDTH - len(self.layers)*130) / max(len(self.layers) - 1, 1)
        geo = {}
        for i, layer in enumerate(self.layers):
            top = self.height/2 + (len(layer)-1)*15
            for j, n in enumerate(layer):
                geo[n] = (i*(130+gap), top - j*30)
        for src, dst, label, dashed in self.edges:
            (sx, sy), (dx, dy) = geo[src], geo[dst]
            self.arrow(sx+130, sy, dx, dy, label, dashed)
        for n, (x, y) in geo.items():
            label, kind = self.nodes[n]
            self.box(x, y-11, 130, 22, [label + (' (database)' if kind == 'database' else '')])

    # Screen sketches: panels separated by '---'; the first line of each panel is its caption.
    def layout_screens(self):
        panels, panel = [], []
        for line in self.block:
            if line.strip() == '---':
                panels.append(panel); panel = []
            else:
                panel.append(line)
        self.panels = panels + [panel]
        self.height = max(len(p) for p in self.panels) * 11 + 20

    def draw_screens(self):
        c = self.canv
        gap = 8
        w = (WIDTH - gap*(len(self.panels)-1)) / len(self.panels)
        for i, (caption, *lines) in enumerate(self.panels):
            x = i*(w+gap)
            c.setFillColor(GREY); c.setFont(italic, 8); c.drawString(x+2, self.height-9, caption)
            c.setStrokeColor(RULE); c.setFillColor(PALE)
            c.roundRect(x, 1, w, self.height-15, 5, fill=1, stroke=1)
            c.setFillColor(INK); c.setFont(body, 8.5)
            for j, line in enumerate(lines):
                c.drawString(x+8, self.height-28-j*11, re.sub(r'\s{2,}', '   ', line.rstrip()))

    # Single screen: text with two columns split at two or more spaces.
    def layout_screen(self):
        self.height = len(self.block) * 16 + 10

    def draw_screen(self):
        c = self.canv
        c.setStrokeColor(RULE); c.setFillColor(PALE)
        c.roundRect(0, 1, WIDTH, self.height-2, 5, fill=1, stroke=1)
        c.setFillColor(INK)
        for i, line in enumerate(self.block):
            parts = re.split(r'\s{2,}', line.strip())
            c.setFont(bold if i == 0 else body, 10 if i == 0 else 9)
            y = self.height - 18 - i*16
            c.drawString(12, y, parts[0])
            if len(parts) > 1:
                c.drawString(WIDTH/2, y, parts[1])

    # Domain model, drawn by hand to match the Mermaid class diagram.
    def layout_domain(self):
        self.height = 100

    def draw_domain(self):
        self.box(0,63,94,32,['Student'])
        self.box(165,63,130,32,['SavedRoute','stops, walks, buffer'])
        self.arrow(94,79,165,79,'1 : 0..*')
        self.box(0,3,125,35,['LatenessEntry','date, start, end'])
        self.box(180,3,120,35,['Appeal','match, token'])
        self.box(355,3,WIDTH-355,35,['DelayRecord','trip, source, delay'])
        self.canv.setStrokeColor(GREY); self.canv.line(47,63,47,38)
        self.canv.setFont(body,7); self.canv.setFillColor(GREY); self.canv.drawString(52,47,'1 : 0..*')
        self.arrow(125,20,180,20,'1 : 0..1')
        self.arrow(300,20,355,20,'0..* : 1')

    def draw(self):
        getattr(self, 'draw_' + self.kind.replace('-', '_'))()

def page_frame(c, doc, total):
    c.saveState(); c.setFont(body,6.8); c.setFillColor(GREY)
    c.drawString(45,A4[1]-23,'LeoWay - System Specification')
    c.drawRightString(A4[0]-45,23,f'Page {doc.page} of {total}')
    c.restoreState()

def make_story():
    lines = SOURCE.read_text(encoding='utf-8').splitlines()
    story = []
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if not line:
            i += 1; continue
        if line == '<!-- page -->':
            story.append(PageBreak())
        elif line.startswith('```'):
            fence = line
            block = []
            i += 1
            while i < len(lines) and not lines[i].startswith('```'):
                block.append(lines[i]); i += 1
            if fence == '```mermaid':
                kind = block.pop(0).removeprefix('%% ').strip()
            else:
                kind = 'screens' if fence == '```screens' else 'screen'
            story.extend([Diagram(kind, block), Spacer(1,6)])
        elif line.startswith('|'):
            block = []
            while i < len(lines) and lines[i].strip().startswith('|'):
                block.append(lines[i]); i += 1
            story.extend([make_table(block), Spacer(1,6)])
            continue
        elif line.startswith('#### '):
            story.append(paragraph(line[5:],'h4'))
        elif line.startswith('### '):
            story.append(paragraph(line[4:],'h3'))
        elif line.startswith('## '):
            story.append(paragraph(line[3:],'h2'))
        elif line.startswith('# '):
            story.append(paragraph(line[2:],'title'))
        else:
            style = 'small' if line.startswith('Version ') else 'body'
            story.append(paragraph(line,style))
        i += 1
    return story

def build(total):
    doc = SimpleDocTemplate(str(OUTPUT),pagesize=A4,leftMargin=54,rightMargin=54,
        topMargin=43,bottomMargin=43,title='LeoWay - System Specification',
        author='LRD (Leo Research Department): Laurenz, Ayan, David, Behrang')
    frame = partial(page_frame, total=total)
    doc.build(make_story(),onFirstPage=frame,onLaterPages=frame,
        canvasmaker=partial(canvas.Canvas,invariant=1))
    return doc.page

if __name__ == '__main__':
    pages = build('?')  # First pass counts the pages for the footer.
    build(pages)
    print(f'{OUTPUT} ({pages} pages)')
