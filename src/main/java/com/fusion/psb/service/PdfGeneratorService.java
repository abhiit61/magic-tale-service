package com.fusion.psb.service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfGeneratorService {

  private static final Logger LOGGER = LoggerFactory.getLogger(PdfGeneratorService.class);

  // Matches: [IMAGE: description]
  private static final Pattern IMAGE_TAG_PATTERN =
      Pattern.compile("^\\[IMAGE:\\s*(.+?)\\s*\\]$", Pattern.CASE_INSENSITIVE);

  // Matches: **(Page N: Illustration - description)**
  private static final Pattern ILLUSTRATION_PATTERN =
      Pattern.compile("^\\*\\*\\(Page \\d+:\\s*Illustration\\s*-\\s*(.+)\\)\\*\\*$", Pattern.CASE_INSENSITIVE);

  private final ImageGenerationService imageGenerationService;

  @Value("${generate.pdf.image.enable}")
  private boolean enableImage;

  @Autowired
  public PdfGeneratorService(ImageGenerationService imageGenerationService) {
    this.imageGenerationService = imageGenerationService;
  }

  public byte[] createPDF(String name, String content, String language) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    Document document = new Document();
    try {
      PdfWriter.getInstance(document, outputStream);
      document.open();

      Font titleFont, h1Font, h2Font, h3Font, bodyFont, boldFont, italicFont, boldItalicFont;
      try {
        BaseFont bfRegular = BaseFont.createFont(resolveRegularFontPath(language), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        BaseFont bfBold    = BaseFont.createFont(resolveBoldFontPath(language),    BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        titleFont      = new Font(bfBold,    20, Font.NORMAL);
        h1Font         = new Font(bfBold,    18, Font.NORMAL);
        h2Font         = new Font(bfBold,    16, Font.NORMAL);
        h3Font         = new Font(bfBold,    14, Font.NORMAL);
        bodyFont       = new Font(bfRegular, 12, Font.NORMAL);
        boldFont       = new Font(bfBold,    12, Font.NORMAL);
        italicFont     = new Font(bfBold,    12, Font.NORMAL);
        boldItalicFont = new Font(bfBold,    12, Font.NORMAL);
      } catch (Exception e) {
        LOGGER.warn("Unicode font not found for language '{}', falling back to Helvetica.", language);
        titleFont      = new Font(Font.FontFamily.HELVETICA, 20, Font.BOLD);
        h1Font         = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
        h2Font         = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD);
        h3Font         = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
        bodyFont       = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL);
        boldFont       = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
        italicFont     = new Font(Font.FontFamily.HELVETICA, 12, Font.ITALIC);
        boldItalicFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLDITALIC);
      }

      // Cover page title
      Paragraph title = new Paragraph("Personalized Storybook for " + name, titleFont);
      title.setSpacingAfter(6);
      document.add(title);
      document.add(new LineSeparator(1f, 100f, BaseColor.BLACK, Element.ALIGN_CENTER, -2f));
      document.add(Chunk.NEWLINE);

      // Parse content line-by-line.
      // pendingNewlines buffers blank lines so they are only flushed when real content follows.
      // This prevents white gaps when an image marker is skipped (disabled or generation failed).
      boolean needNewPage = false;
      int pendingNewlines = 0;
      boolean imageSkipped = false;

      for (String line : content.split("\n")) {
        String trimmed = line.trim();

        if (trimmed.isEmpty()) {
          // Swallow empty lines that immediately follow a skipped image marker
          if (!imageSkipped) pendingNewlines++;
          continue;
        }

        imageSkipped = false;

        // "---" = page boundary marker
        if (trimmed.matches("[-*_]{3,}")) {
          needNewPage = true;
          pendingNewlines = 0;
          continue;
        }

        // Always detect image marker lines so they are never rendered as raw text.
        String imageDesc = extractImageDescription(trimmed);
        if (imageDesc != null) {
          boolean imageAdded = false;
          if (enableImage) {
            if (needNewPage) { document.newPage(); needNewPage = false; pendingNewlines = 0; }
            imageAdded = addStorybookImage(document, imageDesc, pendingNewlines);
          }
          // Whether disabled or generation failed, discard surrounding blank lines
          pendingNewlines = 0;
          if (!imageAdded) imageSkipped = true;
          continue;
        }

        // Apply pending page break before regular content
        if (needNewPage) {
          document.newPage();
          needNewPage = false;
          pendingNewlines = 0;
        }

        // Flush at most one blank line between paragraphs
        if (pendingNewlines > 0) {
          document.add(Chunk.NEWLINE);
          pendingNewlines = 0;
        }

        // Headings
        if (trimmed.startsWith("### ")) {
          Paragraph p = new Paragraph(stripInlineMarkdown(trimmed.substring(4)), h3Font);
          p.setSpacingBefore(8);
          p.setSpacingAfter(3);
          document.add(p);
          continue;
        }
        if (trimmed.startsWith("## ")) {
          Paragraph p = new Paragraph(stripInlineMarkdown(trimmed.substring(3)), h2Font);
          p.setSpacingBefore(10);
          p.setSpacingAfter(4);
          document.add(p);
          continue;
        }
        if (trimmed.startsWith("# ")) {
          Paragraph p = new Paragraph(stripInlineMarkdown(trimmed.substring(2)), h1Font);
          p.setSpacingBefore(12);
          p.setSpacingAfter(5);
          document.add(p);
          continue;
        }

        // Bullet points
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
          Paragraph p = buildStyledParagraph("• " + trimmed.substring(2), bodyFont, boldFont, italicFont, boldItalicFont);
          p.setIndentationLeft(15);
          p.setSpacingAfter(2);
          document.add(p);
          continue;
        }

        // Normal paragraph
        Paragraph p = buildStyledParagraph(trimmed, bodyFont, boldFont, italicFont, boldItalicFont);
        p.setSpacingAfter(4);
        document.add(p);
      }

    } catch (Exception e) {
      LOGGER.error("Error while creating PDF: ", e);
      throw new RuntimeException("Failed to generate the PDF. Please try again later.");
    } finally {
      if (document.isOpen()) {
        document.close();
      }
    }
    return outputStream.toByteArray();
  }

  /**
   * Generates an image and adds it to the document. Leading blank lines are only added
   * when the image is actually available, so callers can discard them when generation fails.
   * Returns true if the image was successfully embedded.
   */
  private boolean addStorybookImage(Document document, String description, int leadingNewlines) {
    try {
      byte[] imageBytes = imageGenerationService.generateStorybookImage(description);
      if (imageBytes == null) return false;

      for (int n = 0; n < leadingNewlines; n++) document.add(Chunk.NEWLINE);

      Image img = Image.getInstance(imageBytes);
      img.setAlignment(Element.ALIGN_CENTER);
      float maxWidth = document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin();
      img.scaleToFit(maxWidth, 300f);
      document.add(img);
      document.add(Chunk.NEWLINE);
      return true;
    } catch (Exception e) {
      LOGGER.warn("Failed to embed image in PDF: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Extracts the scene description from a supported image marker line.
   * Supported formats:
   *   [IMAGE: description]
   *   **(Page N: Illustration - description)**
   */
  private String extractImageDescription(String line) {
    Matcher m1 = IMAGE_TAG_PATTERN.matcher(line);
    if (m1.matches()) return m1.group(1).trim();

    Matcher m2 = ILLUSTRATION_PATTERN.matcher(line);
    if (m2.matches()) return m2.group(1).trim();

    return null;
  }

  private String stripInlineMarkdown(String text) {
    return text.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "$1")
               .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
               .replaceAll("\\*(.+?)\\*", "$1")
               .replaceAll("_(.+?)_", "$1")
               .replaceAll("`(.+?)`", "$1");
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
        if (end != -1) {
          paragraph.add(new Chunk(text.substring(i + 3, end), boldItalic));
          i = end + 3;
        } else {
          buffer.append(text.charAt(i++));
        }
      } else if (i + 1 < text.length() && text.startsWith("**", i)) {
        flushBuffer(paragraph, buffer, normal);
        int end = text.indexOf("**", i + 2);
        if (end != -1) {
          paragraph.add(new Chunk(text.substring(i + 2, end), bold));
          i = end + 2;
        } else {
          buffer.append(text.charAt(i++));
        }
      } else if (text.charAt(i) == '*' || text.charAt(i) == '_') {
        char marker = text.charAt(i);
        flushBuffer(paragraph, buffer, normal);
        int end = text.indexOf(marker, i + 1);
        if (end != -1) {
          paragraph.add(new Chunk(text.substring(i + 1, end), italic));
          i = end + 1;
        } else {
          buffer.append(text.charAt(i++));
        }
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
      case "arabic", "urdu"             -> "/fonts/NotoSansArabic-Bold.ttf";
      case "chinese"                     -> "/fonts/NotoSansSC-Bold.ttf";
      case "japanese"                    -> "/fonts/NotoSansJP-Bold.ttf";
      default                            -> "/fonts/NotoSans-Bold.ttf";
    };
  }
}
