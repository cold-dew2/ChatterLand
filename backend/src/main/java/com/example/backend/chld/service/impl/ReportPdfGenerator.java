package com.example.backend.chld.service.impl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한글 글꼴(나눔고딕, SIL OFL 1.1)을 PDF에 임베딩해 학습 리포트를 만든다.
 * 서버에 설치된 글꼴에 의존하지 않도록 글꼴 파일은 classpath(resources/fonts)에서 읽는다.
 */
@Component
public class ReportPdfGenerator {
    private static final float MARGIN = 48f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2;
    private static final float BOTTOM = MARGIN + 24f;
    private static final Color BRAND = new Color(0x27, 0x4c, 0xc4);
    private static final Color MUTED = new Color(0x6b, 0x72, 0x80);
    private static final Color LINE = new Color(0xe5, 0xe7, 0xeb);
    private static final Color HEADER_FILL = new Color(0xf0, 0xf3, 0xfd);

    private final byte[] regularFont;
    private final byte[] boldFont;

    public ReportPdfGenerator() {
        this.regularFont = readFont("fonts/NanumGothic-Regular.ttf");
        this.boldFont = readFont("fonts/NanumGothic-Bold.ttf");
    }

    /** 리포트 문서 모델. 값이 없는 칸은 호출하는 쪽에서 '미평가'·'기록 없음' 등으로 채운다. */
    public record Section(String title, List<String> paragraphs, List<String> headers, List<List<String>> rows, float[] columnRatios) {
        public static Section text(String title, List<String> paragraphs) { return new Section(title, paragraphs, List.of(), List.of(), new float[0]); }
        public static Section table(String title, List<String> headers, List<List<String>> rows, float[] ratios, List<String> emptyText) {
            return new Section(title, emptyText, headers, rows, ratios);
        }
    }

    public record ReportDocument(String title, List<String[]> info, List<Section> sections, List<String> footnotes) { }

    public byte[] generate(ReportDocument report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Writer writer = new Writer(document,
                    PDType0Font.load(document, new java.io.ByteArrayInputStream(regularFont)),
                    PDType0Font.load(document, new java.io.ByteArrayInputStream(boldFont)));
            writer.title(report.title());
            for (String[] row : report.info()) writer.keyValue(row[0], row[1]);
            writer.gap(10);
            for (Section section : report.sections()) {
                writer.heading(section.title());
                if (section.headers().isEmpty()) section.paragraphs().forEach(writer::paragraph);
                else if (section.rows().isEmpty()) section.paragraphs().forEach(writer::paragraph);
                else writer.table(section.headers(), section.rows(), section.columnRatios());
                writer.gap(8);
            }
            if (!report.footnotes().isEmpty()) {
                writer.heading("안내");
                for (String note : report.footnotes()) writer.paragraph("· " + note, 9f, MUTED);
            }
            writer.finish();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("PDF 생성 실패", e);
        }
    }

    private static byte[] readFont(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) { return input.readAllBytes(); }
        catch (IOException e) { throw new IllegalStateException("리포트 글꼴(" + path + ")을 찾을 수 없습니다.", e); }
    }

    /** 페이지 넘김과 줄바꿈을 처리하는 간단한 레이아웃 도우미 */
    private static final class Writer {
        private final PDDocument document;
        private final PDType0Font regular;
        private final PDType0Font bold;
        private final Map<Integer, Boolean> glyphCache = new HashMap<>();
        private PDPageContentStream stream;
        private float y;

        Writer(PDDocument document, PDType0Font regular, PDType0Font bold) throws IOException {
            this.document = document; this.regular = regular; this.bold = bold;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        private void ensure(float height) throws IOException { if (y - height < BOTTOM) newPage(); }

        void gap(float height) { y -= height; }

        void title(String text) throws IOException {
            write(text, bold, 20f, MARGIN, y - 20f, BRAND);
            y -= 34f;
            line(MARGIN, y, PAGE_WIDTH - MARGIN, y, BRAND, 1.2f);
            y -= 14f;
        }

        void heading(String text) throws IOException {
            ensure(36f);
            write(text, bold, 13f, MARGIN, y - 13f, Color.BLACK);
            y -= 22f;
        }

        void keyValue(String key, String value) throws IOException {
            List<String> lines = wrap(value, regular, 10.5f, CONTENT_WIDTH - 110f);
            ensure(lines.size() * 15f + 2f);
            write(key, bold, 10.5f, MARGIN, y - 10.5f, MUTED);
            for (String text : lines) { write(text, regular, 10.5f, MARGIN + 110f, y - 10.5f, Color.BLACK); y -= 15f; }
        }

        void paragraph(String text) { paragraph(text, 10.5f, Color.BLACK); }

        void paragraph(String text, float size, Color color) {
            try {
                for (String line : wrap(text, regular, size, CONTENT_WIDTH)) {
                    ensure(size + 5f);
                    write(line, regular, size, MARGIN, y - size, color);
                    y -= size + 5f;
                }
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }

        void table(List<String> headers, List<List<String>> rows, float[] ratios) throws IOException {
            float[] widths = new float[headers.size()];
            float total = 0; for (int i = 0; i < widths.length; i++) total += ratios.length == widths.length ? ratios[i] : 1f;
            for (int i = 0; i < widths.length; i++) widths[i] = CONTENT_WIDTH * (ratios.length == widths.length ? ratios[i] : 1f) / total;
            drawRow(headers, widths, true);
            for (List<String> row : rows) drawRow(row, widths, false);
        }

        private void drawRow(List<String> cells, float[] widths, boolean header) throws IOException {
            float size = 9.5f, lineHeight = 13f, padding = 5f;
            PDType0Font font = header ? bold : regular;
            List<List<String>> wrapped = new ArrayList<>();
            int maxLines = 1;
            for (int i = 0; i < widths.length; i++) {
                List<String> lines = wrap(i < cells.size() ? cells.get(i) : "", font, size, widths[i] - padding * 2);
                wrapped.add(lines); maxLines = Math.max(maxLines, lines.size());
            }
            float height = maxLines * lineHeight + padding * 2;
            // 한 행이 페이지보다 길면 줄 단위로 나눠 그리지 않고 다음 페이지로 넘긴다(행 높이는 최대 40줄로 제한).
            if (y - height < BOTTOM) newPage();
            if (header) { stream.setNonStrokingColor(HEADER_FILL); stream.addRect(MARGIN, y - height, CONTENT_WIDTH, height); stream.fill(); }
            float x = MARGIN;
            for (int i = 0; i < widths.length; i++) {
                float lineY = y - padding - size;
                for (String text : wrapped.get(i)) { write(text, font, size, x + padding, lineY, Color.BLACK); lineY -= lineHeight; }
                x += widths[i];
            }
            line(MARGIN, y - height, PAGE_WIDTH - MARGIN, y - height, LINE, 0.6f);
            y -= height;
        }

        void finish() throws IOException {
            stream.close();
            int total = document.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                try (PDPageContentStream footer = new PDPageContentStream(document, document.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    String label = "채터랜드 학습 리포트  ·  " + (i + 1) + " / " + total;
                    float width = regular.getStringWidth(label) / 1000f * 8.5f;
                    footer.beginText();
                    footer.setFont(regular, 8.5f);
                    footer.setNonStrokingColor(MUTED);
                    footer.newLineAtOffset((PAGE_WIDTH - width) / 2f, MARGIN - 18f);
                    footer.showText(label);
                    footer.endText();
                }
            }
        }

        private void write(String text, PDType0Font font, float size, float x, float baseline, Color color) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, baseline);
            stream.showText(sanitize(text, font));
            stream.endText();
        }

        private void line(float x1, float y1, float x2, float y2, Color color, float width) throws IOException {
            stream.setStrokingColor(color); stream.setLineWidth(width);
            stream.moveTo(x1, y1); stream.lineTo(x2, y2); stream.stroke();
        }

        /** 글꼴에 없는 문자(이모지 등)는 공백으로 바꿔 PDF 생성이 실패하지 않게 한다. */
        private String sanitize(String text, PDType0Font font) {
            if (text == null) return "";
            StringBuilder out = new StringBuilder();
            text.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').codePoints().forEach(cp -> {
                boolean ok = glyphCache.computeIfAbsent(cp, code -> {
                    try { font.encode(new String(Character.toChars(code))); return true; }
                    catch (IOException | IllegalArgumentException e) { return false; }
                });
                out.append(ok ? new String(Character.toChars(cp)) : " ");
            });
            return out.toString();
        }

        private float width(String text, PDType0Font font, float size) throws IOException {
            return font.getStringWidth(sanitize(text, font)) / 1000f * size;
        }

        /** 공백 우선, 공백이 없으면 글자 단위로 줄을 나눈다. */
        private List<String> wrap(String text, PDType0Font font, float size, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            String value = text == null ? "" : text.replace('\n', ' ');
            if (value.isEmpty()) { lines.add(""); return lines; }
            StringBuilder current = new StringBuilder();
            for (String word : value.split("(?<= )")) {
                if (width(current + word, font, size) <= maxWidth) { current.append(word); continue; }
                if (!current.isEmpty()) { lines.add(current.toString().stripTrailing()); current.setLength(0); }
                for (int offset = 0; offset < word.length(); ) {
                    int cp = word.codePointAt(offset);
                    String ch = new String(Character.toChars(cp));
                    if (width(current + ch, font, size) > maxWidth && !current.isEmpty()) { lines.add(current.toString()); current.setLength(0); }
                    current.append(ch);
                    offset += Character.charCount(cp);
                }
            }
            if (!current.isEmpty()) lines.add(current.toString().stripTrailing());
            return lines.size() > 40 ? new ArrayList<>(lines.subList(0, 40)) : lines;
        }
    }
}
