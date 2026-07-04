package io.kelta.worker.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Streams a simple tabular PDF: a title on the first page, a bold header row
 * repeated on every page, one table row per {@link #writeRow}, and a
 * "Page n" footer. Landscape A4; equal column widths; cell text truncated
 * with an ellipsis when it exceeds its column.
 *
 * <p>Rows are laid out as they arrive so arbitrarily large reports never hold
 * more than one page of content in memory before {@link #close()} writes the
 * document to the output stream (PDFBox buffers page content streams, but no
 * row data is retained by this class).
 */
public final class PdfTableWriter implements Closeable {

    private static final PDRectangle PAGE_SIZE = new PDRectangle(
            PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()); // landscape
    private static final float MARGIN = 36f;
    private static final float TITLE_SIZE = 14f;
    private static final float FONT_SIZE = 8f;
    private static final float ROW_HEIGHT = 14f;
    private static final PDFont FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final OutputStream out;
    private final String title;
    private final List<String> headers;
    private final float columnWidth;

    private final PDDocument document = new PDDocument();
    private PDPageContentStream content;
    private float cursorY;
    private int pageNumber;

    public PdfTableWriter(OutputStream out, String title, List<String> headers) throws IOException {
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("PDF table needs at least one column");
        }
        this.out = out;
        this.title = title != null ? title : "Report";
        this.headers = List.copyOf(headers);
        this.columnWidth = (PAGE_SIZE.getWidth() - 2 * MARGIN) / headers.size();
        startPage();
    }

    /** Writes one table row, starting a new page when the current one is full. */
    public void writeRow(List<String> cells) throws IOException {
        if (cursorY - ROW_HEIGHT < MARGIN) {
            endPage();
            startPage();
        }
        drawRow(cells, FONT);
    }

    @Override
    public void close() throws IOException {
        endPage();
        document.save(out);
        document.close();
    }

    // -------------------------------------------------------------------------

    private void startPage() throws IOException {
        PDPage page = new PDPage(PAGE_SIZE);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        pageNumber++;
        cursorY = PAGE_SIZE.getHeight() - MARGIN;

        if (pageNumber == 1) {
            content.beginText();
            content.setFont(FONT_BOLD, TITLE_SIZE);
            content.newLineAtOffset(MARGIN, cursorY - TITLE_SIZE);
            content.showText(sanitize(title));
            content.endText();
            cursorY -= TITLE_SIZE + 10f;
        }

        drawRow(headers, FONT_BOLD);
    }

    private void endPage() throws IOException {
        content.beginText();
        content.setFont(FONT, FONT_SIZE);
        content.newLineAtOffset(MARGIN, MARGIN / 2);
        content.showText("Page " + pageNumber);
        content.endText();
        content.close();
    }

    private void drawRow(List<String> cells, PDFont font) throws IOException {
        float y = cursorY - ROW_HEIGHT + (ROW_HEIGHT - FONT_SIZE) / 2;
        for (int i = 0; i < headers.size(); i++) {
            String cell = i < cells.size() ? cells.get(i) : "";
            content.beginText();
            content.setFont(font, FONT_SIZE);
            content.newLineAtOffset(MARGIN + i * columnWidth + 2f, y);
            content.showText(truncate(sanitize(cell), font));
            content.endText();
        }
        cursorY -= ROW_HEIGHT;
    }

    /**
     * PDFBox's WinAnsi text drawing throws on control characters (incl.
     * newlines) and unmappable glyphs — flatten whitespace and replace
     * anything outside the encodable range.
     */
    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.replaceAll("\\s+", " ").toCharArray()) {
            sb.append(c >= 32 && c < 127 || (c >= 160 && c <= 255) ? c : '?');
        }
        return sb.toString();
    }

    private String truncate(String value, PDFont font) throws IOException {
        float maxWidth = columnWidth - 4f;
        if (textWidth(value, font) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int end = value.length();
        while (end > 0 && textWidth(value.substring(0, end) + ellipsis, font) > maxWidth) {
            end--;
        }
        return value.substring(0, end) + ellipsis;
    }

    private static float textWidth(String value, PDFont font) throws IOException {
        return font.getStringWidth(value) / 1000 * FONT_SIZE;
    }
}
