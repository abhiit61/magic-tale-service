package com.fusion.psb.service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class PdfGeneratorService {

  private static final Logger LOGGER = LoggerFactory.getLogger(PdfGeneratorService.class);

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
        // Indic/CJK scripts have no italic — bold is used as the closest substitute
        titleFont      = new Font(bfBold,    20, Font.NORMAL);
        h1Font         = new Font(bfBold,    18, Font.NORMAL);
        h2Font         = new Font(bfBold,    16, Font.NORMAL);
        h3Font         = new Font(bfBold,    14, Font.NORMAL);
        bodyFont       = new Font(bfRegular, 12, Font.NORMAL);
        boldFont       = new Font(bfBold,    12, Font.NORMAL);
        italicFont     = new Font(bfBold,    12, Font.NORMAL);  // bold as italic fallback
        boldItalicFont = new Font(bfBold,    12, Font.NORMAL);
      } catch (Exception e) {
        LOGGER.warn("Unicode font not found for language '{}', falling back to Helvetica. Place font files in src/main/resources/fonts/", language);
        titleFont      = new Font(Font.FontFamily.HELVETICA, 20, Font.BOLD);
        h1Font         = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
        h2Font         = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD);
        h3Font         = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
        bodyFont       = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL);
        boldFont       = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
        italicFont     = new Font(Font.FontFamily.HELVETICA, 12, Font.ITALIC);
        boldItalicFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLDITALIC);
      }

      // Cover title
      Paragraph title = new Paragraph("Personalized Storybook for " + name, titleFont);
      title.setSpacingAfter(6);
      document.add(title);
      document.add(new LineSeparator(1f, 100f, BaseColor.BLACK, Element.ALIGN_CENTER, -2f));
      document.add(Chunk.NEWLINE);

      // Parse and render markdown content line by line
      for (String line : content.split("\n")) {
        String trimmed = line.trim();

        if (trimmed.isEmpty()) {
          document.add(Chunk.NEWLINE);
          continue;
        }

        // Horizontal rule: --- or *** or ___
        if (trimmed.matches("[-*_]{3,}")) {
          document.add(Chunk.NEWLINE);
          document.add(new LineSeparator(0.5f, 100f, BaseColor.GRAY, Element.ALIGN_CENTER, -2f));
          document.add(Chunk.NEWLINE);
          continue;
        }

        // Headings — strip inline markers since heading font is already bold
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

        // Normal paragraph with inline bold/italic
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

  /** Removes inline markdown markers (bold/italic/code) from heading text. */
  private String stripInlineMarkdown(String text) {
    return text.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "$1")
               .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
               .replaceAll("\\*(.+?)\\*", "$1")
               .replaceAll("_(.+?)_", "$1")
               .replaceAll("`(.+?)`", "$1");
  }

  /**
   * Builds a Paragraph by scanning for inline markdown markers (***bold-italic***,
   * **bold**, *italic*, _italic_) and wrapping matched spans in the appropriate Font.
   */
  private Paragraph buildStyledParagraph(String text, Font normal, Font bold, Font italic, Font boldItalic) {
    Paragraph paragraph = new Paragraph();
    paragraph.setFont(normal);
    StringBuilder buffer = new StringBuilder();
    int i = 0;

    while (i < text.length()) {
      // ***bold-italic***
      if (i + 2 < text.length() && text.startsWith("***", i)) {
        flushBuffer(paragraph, buffer, normal);
        int end = text.indexOf("***", i + 3);
        if (end != -1) {
          paragraph.add(new Chunk(text.substring(i + 3, end), boldItalic));
          i = end + 3;
        } else {
          buffer.append(text.charAt(i++));
        }
      }
      // **bold**
      else if (i + 1 < text.length() && text.startsWith("**", i)) {
        flushBuffer(paragraph, buffer, normal);
        int end = text.indexOf("**", i + 2);
        if (end != -1) {
          paragraph.add(new Chunk(text.substring(i + 2, end), bold));
          i = end + 2;
        } else {
          buffer.append(text.charAt(i++));
        }
      }
      // *italic* or _italic_
      else if (text.charAt(i) == '*' || text.charAt(i) == '_') {
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
      case "arabic", "urdu"              -> "/fonts/NotoSansArabic-Bold.ttf";
      case "chinese"                     -> "/fonts/NotoSansSC-Bold.ttf";
      case "japanese"                    -> "/fonts/NotoSansJP-Bold.ttf";
      default                            -> "/fonts/NotoSans-Bold.ttf";
    };
  }
}
