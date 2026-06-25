package com.fusion.psb.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfGeneratorService {

  private static final Logger LOGGER = LoggerFactory.getLogger(PdfGeneratorService.class);

  private static final Pattern IMAGE_TAG_PATTERN =
      Pattern.compile("^\\[IMAGE:\\s*(.+?)\\s*\\]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern ILLUSTRATION_PATTERN =
      Pattern.compile("^\\*\\*\\(Page \\d+:\\s*Illustration\\s*-\\s*(.+)\\)\\*\\*$", Pattern.CASE_INSENSITIVE);

  // Color palette
  private static final BaseColor NAVY = new BaseColor(23,  37,  84);
  private static final BaseColor GOLD = new BaseColor(202, 138,   4);
  private static final BaseColor INK  = new BaseColor(30,  30,  46);

  // Page margins — extra top/bottom headroom for header and footer bands
  private static final float MH = 55f;
  private static final float MT = 70f;
  private static final float MB = 60f;

  private final ImageGenerationService imageGenerationService;
  private final Executor imageTaskExecutor;

  @Value("${generate.pdf.image.enable}")
  private boolean enableImage;

  @Autowired
  public PdfGeneratorService(ImageGenerationService imageGenerationService,
                             @Qualifier("imageGenerationTaskExecutor") Executor imageTaskExecutor) {
    this.imageGenerationService = imageGenerationService;
    this.imageTaskExecutor = imageTaskExecutor;
  }

  public byte[] createPDF(String name, String content, String language) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    Document document = new Document(PageSize.A4, MH, MH, MT, MB);
    try {
      PdfWriter writer = PdfWriter.getInstance(document, outputStream);
      writer.setPageEvent(new StorybookPageEvent(name));
      document.open();

      Font coverFont, h1Font, h2Font, h3Font, bodyFont, boldFont, italicFont, boldItalicFont;
      try {
        BaseFont bfReg  = BaseFont.createFont(resolveRegularFontPath(language), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        BaseFont bfBold = BaseFont.createFont(resolveBoldFontPath(language),    BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        coverFont      = new Font(bfBold,  34, Font.NORMAL, BaseColor.WHITE);
        h1Font         = new Font(bfBold,  24, Font.NORMAL, NAVY);
        h2Font         = new Font(bfBold,  20, Font.NORMAL, NAVY);
        h3Font         = new Font(bfBold,  17, Font.NORMAL, NAVY);
        bodyFont       = new Font(bfReg,   17, Font.NORMAL, INK);
        boldFont       = new Font(bfBold,  17, Font.NORMAL, INK);
        italicFont     = new Font(bfBold,  17, Font.NORMAL, INK);
        boldItalicFont = new Font(bfBold,  17, Font.NORMAL, INK);
      } catch (Exception e) {
        LOGGER.warn("Unicode font not found for '{}', falling back to Helvetica.", language);
        coverFont      = new Font(Font.FontFamily.HELVETICA, 34, Font.BOLD,      BaseColor.WHITE);
        h1Font         = new Font(Font.FontFamily.HELVETICA, 24, Font.BOLD,      NAVY);
        h2Font         = new Font(Font.FontFamily.HELVETICA, 20, Font.BOLD,      NAVY);
        h3Font         = new Font(Font.FontFamily.HELVETICA, 17, Font.BOLD,      NAVY);
        bodyFont       = new Font(Font.FontFamily.HELVETICA, 17, Font.NORMAL,    INK);
        boldFont       = new Font(Font.FontFamily.HELVETICA, 17, Font.BOLD,      INK);
        italicFont     = new Font(Font.FontFamily.HELVETICA, 17, Font.ITALIC,    INK);
        boldItalicFont = new Font(Font.FontFamily.HELVETICA, 17, Font.BOLDITALIC,INK);
      }

      // Extract story title from the first # heading so it can go on the cover
      String storyTitle = "";
      boolean titleConsumed = false;
      for (String line : content.split("\n")) {
        String t = line.trim();
        if (t.startsWith("# ")) {
          storyTitle = stripInlineMarkdown(t.substring(2)).trim();
          break;
        }
      }

      addCoverPage(document, name, storyTitle, coverFont);

      Map<String, CompletableFuture<byte[]>> imageFutures = initializeImageFutures(content);
      boolean needNewPage  = true;  // first content block always opens a fresh page after the cover
      int     pendingNewlines = 0;
      boolean imageSkipped = false;

      for (String line : content.split("\n")) {
        String trimmed = line.trim();

        // Skip the title heading — it is already rendered on the cover page
        if (!titleConsumed && trimmed.startsWith("# ") &&
                stripInlineMarkdown(trimmed.substring(2)).trim().equals(storyTitle)) {
          titleConsumed = true;
          continue;
        }

        if (trimmed.isEmpty()) {
          if (!imageSkipped) pendingNewlines++;
          continue;
        }

        imageSkipped = false;

        if (trimmed.matches("[-*_]{3,}")) {
          needNewPage     = true;
          pendingNewlines = 0;
          continue;
        }

        String imageDesc = extractImageDescription(trimmed);
        if (imageDesc != null) {
          boolean imageAdded = false;
          if (enableImage) {
            if (needNewPage) { document.newPage(); needNewPage = false; pendingNewlines = 0; }
            byte[] imageBytes = resolveImageBytes(imageDesc, imageFutures);
            imageAdded = addStorybookImage(document, imageBytes, pendingNewlines);
          }
          pendingNewlines = 0;
          if (!imageAdded) imageSkipped = true;
          continue;
        }

        if (needNewPage) { document.newPage(); needNewPage = false; pendingNewlines = 0; }
        if (pendingNewlines > 0) { document.add(Chunk.NEWLINE); pendingNewlines = 0; }

        if (trimmed.startsWith("### ")) {
          Paragraph p = new Paragraph(stripInlineMarkdown(trimmed.substring(4)), h3Font);
          p.setSpacingBefore(12); p.setSpacingAfter(6);
          document.add(p);
          continue;
        }
        if (trimmed.startsWith("## ")) {
          Paragraph p = new Paragraph(stripInlineMarkdown(trimmed.substring(3)), h2Font);
          p.setSpacingBefore(14); p.setSpacingAfter(8);
          document.add(p);
          document.add(new LineSeparator(1.5f, 60f, GOLD, Element.ALIGN_LEFT, -2f));
          document.add(Chunk.NEWLINE);
          continue;
        }
        if (trimmed.startsWith("# ")) {
          addPageHeading(document, stripInlineMarkdown(trimmed.substring(2)), h1Font);
          continue;
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
          Paragraph p = buildStyledParagraph("• " + trimmed.substring(2), bodyFont, boldFont, italicFont, boldItalicFont);
          p.setIndentationLeft(20);
          p.setLeading(28f);
          p.setSpacingAfter(8);
          document.add(p);
          continue;
        }

        Paragraph p = buildStyledParagraph(trimmed, bodyFont, boldFont, italicFont, boldItalicFont);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        p.setLeading(30f);
        p.setSpacingAfter(14);
        document.add(p);
      }

    } catch (Exception e) {
      LOGGER.error("Error while creating PDF: ", e.getMessage());
      throw new RuntimeException("Failed to generate the PDF. Please try again later.");
    } finally {
      if (document.isOpen()) document.close();
    }
    return outputStream.toByteArray();
  }

  // ── Cover page ────────────────────────────────────────────────────────────

  private void addCoverPage(Document document, String name, String storyTitle, Font coverFont) throws DocumentException {
    for (int i = 0; i < 6; i++) document.add(Chunk.NEWLINE);

    // Navy title banner
    PdfPTable banner = new PdfPTable(1);
    banner.setWidthPercentage(100);
    PdfPCell titleCell = new PdfPCell();
    titleCell.setBackgroundColor(NAVY);
    titleCell.setPadding(36f);
    titleCell.setBorder(0);
    titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);

    Paragraph namePara = new Paragraph(name + "'s\nStorybook", coverFont);
    namePara.setAlignment(Element.ALIGN_CENTER);
    namePara.setLeading(46f);
    titleCell.addElement(namePara);

    if (storyTitle != null && !storyTitle.isBlank()) {
      Font storyTitleFont = new Font(Font.FontFamily.HELVETICA, 16, Font.ITALIC, GOLD);
      Paragraph storyTitlePara = new Paragraph(storyTitle, storyTitleFont);
      storyTitlePara.setAlignment(Element.ALIGN_CENTER);
      storyTitlePara.setSpacingBefore(18f);
      titleCell.addElement(storyTitlePara);
    }

    banner.addCell(titleCell);
    document.add(banner);

    document.add(Chunk.NEWLINE);
    document.add(new LineSeparator(4f, 55f, GOLD, Element.ALIGN_CENTER, 0));
    document.add(Chunk.NEWLINE);

    Font subtitleFont = new Font(Font.FontFamily.HELVETICA, 13, Font.ITALIC, NAVY);
    Paragraph subtitle = new Paragraph("~ A Personalized Adventure ~", subtitleFont);
    subtitle.setAlignment(Element.ALIGN_CENTER);
    document.add(subtitle);

    document.add(Chunk.NEWLINE);
    document.add(new LineSeparator(4f, 55f, GOLD, Element.ALIGN_CENTER, 0));
  }

  // ── Page heading (# level) with gold underline ───────────────────────────

  private void addPageHeading(Document document, String text, Font font) throws DocumentException {
    Paragraph heading = new Paragraph(text, font);
    heading.setAlignment(Element.ALIGN_CENTER);
    heading.setSpacingBefore(6);
    heading.setSpacingAfter(6);
    document.add(heading);
    document.add(new LineSeparator(2.5f, 85f, GOLD, Element.ALIGN_CENTER, -2f));
    document.add(Chunk.NEWLINE);
  }

  // ── Image with gold frame ─────────────────────────────────────────────────

  private boolean addStorybookImage(Document document, byte[] imageBytes, int leadingNewlines) {
    try {
      if (imageBytes == null || imageBytes.length == 0) {
        return false;
      }

      for (int n = 0; n < leadingNewlines; n++) document.add(Chunk.NEWLINE);

      Image img = Image.getInstance(imageBytes);
      float maxWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
      img.scaleToFit(maxWidth, 370f);
      img.setAlignment(Element.ALIGN_CENTER);

      // Gold border frame around the illustration
      PdfPTable frame = new PdfPTable(1);
      frame.setWidthPercentage(100);
      PdfPCell cell = new PdfPCell();
      cell.addElement(img);
      cell.setBorderColor(GOLD);
      cell.setBorderWidth(2.5f);
      cell.setPadding(5f);
      cell.setHorizontalAlignment(Element.ALIGN_CENTER);
      frame.addCell(cell);
      document.add(frame);
      document.add(Chunk.NEWLINE);
      return true;
    } catch (Exception e) {
      LOGGER.warn("Failed to embed image in PDF: {}", e.getMessage());
      return false;
    }
  }

  // ── Per-page header and footer ────────────────────────────────────────────

  private static class StorybookPageEvent extends PdfPageEventHelper {

    private final String storyName;

    StorybookPageEvent(String storyName) {
      this.storyName = storyName;
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
      if (writer.getPageNumber() == 1) return; // cover page has no header/footer

      PdfContentByte cb  = writer.getDirectContent();
      float pageW = document.getPageSize().getWidth();
      float left  = document.leftMargin();
      float right = pageW - document.rightMargin();

      // ── header ──────────────────────────────────────────────────────────
      cb.setColorStroke(GOLD);
      cb.setLineWidth(1.5f);
      cb.moveTo(left, document.top() + 22);
      cb.lineTo(right, document.top() + 22);
      cb.stroke();

      ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
          new Phrase(storyName + "'s Story",
              new Font(Font.FontFamily.HELVETICA, 8, Font.ITALIC, NAVY)),
          pageW / 2f, document.top() + 34, 0);

      // ── footer ──────────────────────────────────────────────────────────
      cb.setColorStroke(GOLD);
      cb.setLineWidth(1f);
      cb.moveTo(left, document.bottom() - 18);
      cb.lineTo(right, document.bottom() - 18);
      cb.stroke();

      // page number minus cover page
      ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
          new Phrase("- " + (writer.getPageNumber() - 1) + " -",
              new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, NAVY)),
          pageW / 2f, document.bottom() - 32, 0);
    }
  }

  // ── Markdown helpers ──────────────────────────────────────────────────────

  private String extractImageDescription(String line) {
    Matcher m1 = IMAGE_TAG_PATTERN.matcher(line);
    if (m1.matches()) return m1.group(1).trim();
    Matcher m2 = ILLUSTRATION_PATTERN.matcher(line);
    if (m2.matches()) return m2.group(1).trim();
    return null;
  }

  private String stripInlineMarkdown(String text) {
    return text.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "$1")
               .replaceAll("\\*\\*(.+?)\\*\\*",       "$1")
               .replaceAll("\\*(.+?)\\*",              "$1")
               .replaceAll("_(.+?)_",                  "$1")
               .replaceAll("`(.+?)`",                  "$1");
  }

  private Map<String, CompletableFuture<byte[]>> initializeImageFutures(String content) {
    if (!enableImage) {
      return Map.of();
    }
    Map<String, CompletableFuture<byte[]>> futures = new LinkedHashMap<>();
    for (String line : content.split("\n")) {
      String trimmed = line.trim();
      String description = extractImageDescription(trimmed);
      if (description != null && !futures.containsKey(description)) {
        futures.put(description, CompletableFuture.supplyAsync(() -> {
          try {
            return imageGenerationService.generateStorybookImage(description);
          } catch (Exception e) {
            LOGGER.warn("Parallel image generation failed for '{}': {}", ImageGenerationService.sha256(description), e.getMessage());
            return null;
          }
        }, imageTaskExecutor));
      }
    }
    return futures;
  }

  private byte[] resolveImageBytes(String description, Map<String, CompletableFuture<byte[]>> imageFutures) {
    CompletableFuture<byte[]> future = imageFutures.get(description);
    if (future == null) {
      return null;
    }
    try {
      return future.get(90, TimeUnit.SECONDS);
    } catch (Exception e) {
      LOGGER.warn("Timed out or failed to retrieve generated image for '{}': {}", description, e.getMessage());
      return null;
    }
  }

  private Paragraph buildStyledParagraph(String text, Font normal, Font bold, Font italic, Font boldItalic) {
    Paragraph paragraph = new Paragraph();
    paragraph.setFont(normal);
    StringBuilder buffer = new StringBuilder();
    int i = 0;
    while (i < text.length()) {
      if (i + 2 < text.length() && text.startsWith("***", i)) {
        flushBuffer(paragraph, buffer, normal);
        int end = text.indexOf("***", i + 3);
        if (end != -1) { paragraph.add(new Chunk(text.substring(i + 3, end), boldItalic)); i = end + 3; }
        else buffer.append(text.charAt(i++));
      } else if (i + 1 < text.length() && text.startsWith("**", i)) {
        flushBuffer(paragraph, buffer, normal);
        int end = text.indexOf("**", i + 2);
        if (end != -1) { paragraph.add(new Chunk(text.substring(i + 2, end), bold)); i = end + 2; }
        else buffer.append(text.charAt(i++));
      } else if (text.charAt(i) == '*' || text.charAt(i) == '_') {
        char marker = text.charAt(i);
        flushBuffer(paragraph, buffer, normal);
        int end = text.indexOf(marker, i + 1);
        if (end != -1) { paragraph.add(new Chunk(text.substring(i + 1, end), italic)); i = end + 1; }
        else buffer.append(text.charAt(i++));
      } else {
        buffer.append(text.charAt(i++));
      }
    }
    flushBuffer(paragraph, buffer, normal);
    return paragraph;
  }

  private void flushBuffer(Paragraph paragraph, StringBuilder buffer, Font font) {
    if (buffer.length() > 0) {
      paragraph.add(new Chunk(buffer.toString(), font));
      buffer.setLength(0);
    }
  }

  // ── Font resolution ───────────────────────────────────────────────────────

  private String resolveRegularFontPath(String language) {
    return switch (language.toLowerCase()) {
      case "hindi", "marathi", "nepali" -> "/fonts/NotoSansDevanagari-Regular.ttf";
      case "tamil"                       -> "/fonts/NotoSansTamil-Regular.ttf";
      case "telugu"                      -> "/fonts/NotoSansTelugu-Regular.ttf";
      case "kannada"                     -> "/fonts/NotoSansKannada-Regular.ttf";
      case "malayalam"                   -> "/fonts/NotoSansMalayalam-Regular.ttf";
      case "bengali"                     -> "/fonts/NotoSansBengali-Regular.ttf";
      case "gujarati"                    -> "/fonts/NotoSansGujarati-Regular.ttf";
      case "punjabi"                     -> "/fonts/NotoSansGurmukhi-Regular.ttf";
      case "arabic", "urdu"              -> "/fonts/NotoSansArabic-Regular.ttf";
      case "chinese"                     -> "/fonts/NotoSansSC-Regular.ttf";
      case "japanese"                    -> "/fonts/NotoSansJP-Regular.ttf";
      default                            -> "/fonts/NotoSans-Regular.ttf";
    };
  }

  private String resolveBoldFontPath(String language) {
    return switch (language.toLowerCase()) {
      case "hindi", "marathi", "nepali" -> "/fonts/NotoSansDevanagari-Bold.ttf";
      case "tamil"                       -> "/fonts/NotoSansTamil-Bold.ttf";
      case "telugu"                      -> "/fonts/NotoSansTelugu-Bold.ttf";
      case "kannada"                     -> "/fonts/NotoSansKannada-Bold.ttf";
      case "malayalam"                   -> "/fonts/NotoSansMalayalam-Bold.ttf";
      case "bengali"                     -> "/fonts/NotoSansBengali-Bold.ttf";
      case "gujarati"                    -> "/fonts/NotoSansGujarati-Bold.ttf";
      case "punjabi"                     -> "/fonts/NotoSansGurmukhi-Bold.ttf";
      case "arabic", "urdu"              -> "/fonts/NotoSansArabic-Bold.ttf";
      case "chinese"                     -> "/fonts/NotoSansSC-Bold.ttf";
      case "japanese"                    -> "/fonts/NotoSansJP-Bold.ttf";
      default                            -> "/fonts/NotoSans-Bold.ttf";
    };
  }
}
