"""Build the five-page specification from Markdown; no network or student data needed.

Run: python docs/tools/build_spec.py
Dependency: python -m pip install -r docs/tools/requirements.txt
"""
from pathlib import Path
from functools import partial
from xml.sax.saxutils import escape
import os
import re

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
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
body, bold = 'TimesNewRoman', 'TimesNewRoman-Bold'
pdfmetrics.registerFontFamily(body, normal=body, bold=bold,
    italic='TimesNewRoman-Italic', boldItalic='TimesNewRoman-BoldItalic')
STYLES = {
    'title': ParagraphStyle('title', fontName=bold, fontSize=20, leading=26, textColor=INK, spaceAfter=10),
    'h2': ParagraphStyle('h2', fontName=bold, fontSize=16, leading=21, textColor=INK, spaceBefore=12, spaceAfter=6, keepWithNext=True),
    'h3': ParagraphStyle('h3', fontName=bold, fontSize=12.5, leading=17, textColor=INK, spaceBefore=10, spaceAfter=5, keepWithNext=True),
    'body': ParagraphStyle('body', fontName=body, fontSize=12, leading=16, textColor=INK, spaceAfter=6),
    'small': ParagraphStyle('small', fontName=body, fontSize=8.5, leading=11.5, textColor=GREY, spaceAfter=5),
    'cell': ParagraphStyle('cell', fontName=body, fontSize=9.5, leading=12.5, textColor=INK),
    'head': ParagraphStyle('head', fontName=bold, fontSize=9.5, leading=12.5, textColor=INK),
}

def inline(text):
    text = escape(text)
    text = re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', text)
    return re.sub(r'\[([^\]]+)\]\(([^)]+)\)', r'<link href="\2" color="#454545"><u>\1</u></link>', text)

def paragraph(text, style='body'):
    return Paragraph(inline(text), STYLES[style])

def make_table(lines):
    rows = [[part.strip() for part in line.strip().strip('|').split('|')] for line in lines]
    rows.pop(1)  # Markdown separator.
    first = 90 if rows[0][0] == 'Test' else 104 if rows[0][0] == 'Term' else 42
    data = [[paragraph(c, 'head' if i == 0 else 'cell') for c in row] for i, row in enumerate(rows)]
    result = Table(data, colWidths=[first, WIDTH-first], repeatRows=1, hAlign='LEFT')
    result.setStyle(TableStyle([
        ('VALIGN',(0,0),(-1,-1),'TOP'), ('BACKGROUND',(0,0),(-1,0),PALE),
        ('LEFTPADDING',(0,0),(-1,-1),7), ('RIGHTPADDING',(0,0),(-1,-1),7),
        ('TOPPADDING',(0,0),(-1,-1),5), ('BOTTOMPADDING',(0,0),(-1,-1),5),
        ('LINEBELOW',(0,0),(-1,-1),0.4,RULE),
    ]))
    return result

class Diagram(Flowable):
    """Small vector diagrams matching the Markdown use cases, model and UI sketch."""
    def __init__(self, kind):
        super().__init__()
        self.kind = kind
        self.width = WIDTH
        self.height = {'use-cases': 92, 'domain': 100, 'screen': 102}[kind]

    def box(self, x, y, w, h, lines):
        c = self.canv
        c.setFillColor(PALE); c.setStrokeColor(RULE)
        c.roundRect(x, y, w, h, 4, fill=1, stroke=1)
        c.setFillColor(INK); c.setFont(body, 8.5)
        for i, line in enumerate(lines):
            c.drawCentredString(x+w/2, y+h/2+(len(lines)-1)*5-i*10-3, line)

    def arrow(self, x1, y1, x2, y2, label=''):
        c = self.canv
        c.setStrokeColor(GREY); c.setFillColor(GREY); c.setLineWidth(0.65)
        c.line(x1,y1,x2,y2)
        c.line(x2,y2,x2-4,y2+2); c.line(x2,y2,x2-4,y2-2)
        if label:
            c.setFont(body,7); c.drawCentredString((x1+x2)/2, y1+4, label)

    def draw(self):
        if self.kind == 'use-cases':
            for y, actor, actions in [
                (62,'Student',['Plan journey / get alerts','Report disruption / draft appeal']),
                (32,'Teacher',['Check shared record','Handle appeals (optional)']),
                (2,'Administrator',['Manage feeds / check health']),
            ]:
                self.box(0,y,100,26,[actor]); self.arrow(100,y+13,155,y+13)
                self.box(155,y,WIDTH-155,26,actions)
        elif self.kind == 'domain':
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
        else:
            c = self.canv
            c.setStrokeColor(RULE); c.setFillColor(PALE)
            c.roundRect(0,1,WIDTH,99,5,fill=1,stroke=1)
            c.setFillColor(INK); c.setFont(bold,10); c.drawString(12,82,'Today')
            c.setFont(body,8); c.drawRightString(WIDTH-12,82,'Last sync 05:45')
            c.setFont(body,9)
            c.drawString(12,63,'First lesson  08:55'); c.drawString(190,63,'Leave by  07:42')
            c.drawString(12,46,'Bus 19  +6 min')
            c.drawString(12,28,'[View another connection]'); c.drawString(250,28,'[Report disruption]')
            c.drawString(12,12,'[My lateness / draft appeal]'); c.drawString(250,12,'[Route and alerts]')

def page_frame(c, doc):
    c.saveState(); c.setFont(body,6.8); c.setFillColor(GREY)
    c.drawString(45,A4[1]-23,'LeoWay - System Specification')
    c.drawRightString(A4[0]-45,23,f'Page {doc.page} of 5')
    c.restoreState()

def build():
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
            kind = block[0].removeprefix('%% ').strip() if fence == '```mermaid' else 'screen'
            story.extend([Diagram(kind),Spacer(1,3)])
        elif line.startswith('|'):
            block = []
            while i < len(lines) and lines[i].strip().startswith('|'):
                block.append(lines[i]); i += 1
            story.append(make_table(block))
            continue
        elif line.startswith('### '):
            story.append(paragraph(line[4:],'h3'))
        elif line.startswith('## '):
            story.append(paragraph(line[3:],'h2'))
        elif line.startswith('# '):
            story.append(paragraph(line[2:],'title'))
        else:
            style = 'small' if ' · Version ' in line or line.startswith('Scope: [') else 'body'
            story.append(paragraph(line,style))
        i += 1
    doc = SimpleDocTemplate(str(OUTPUT),pagesize=A4,leftMargin=54,rightMargin=54,
        topMargin=43,bottomMargin=43,title='LeoWay - System Specification',
        author='Laurenz, Ayan, David, Behrang')
    doc.build(story,onFirstPage=page_frame,onLaterPages=page_frame,
        canvasmaker=partial(canvas.Canvas,invariant=1))
    if doc.page != 5:
        raise RuntimeError(f'Expected five pages, got {doc.page}. Shorten the text or adjust spacing before publishing.')
    print(OUTPUT)

if __name__ == '__main__':
    build()
