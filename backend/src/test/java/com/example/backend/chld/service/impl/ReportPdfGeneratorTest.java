package com.example.backend.chld.service.impl;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportPdfGeneratorTest {
    private final ReportPdfGenerator generator = new ReportPdfGenerator();

    private ReportPdfGenerator.ReportDocument document(int reviewRows) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < reviewRows; i++)
            rows.add(List.of("2026-10-0" + (i % 9 + 1) + " 10:2" + (i % 10), "ㄹ 발음 / 라디오", "라디오", "PRONUNCIATION".isEmpty() ? "" : "계산 안 함",
                    "연습 필요 / ㄹ 받침 소리가 약해서 다음 수업에서 혀 위치를 다시 연습하기로 했습니다. 아주 긴 메모가 표 칸을 넘지 않고 여러 줄로 나뉘는지 확인합니다. 🙂"));
        return new ReportPdfGenerator.ReportDocument("채터랜드 학습 리포트", List.<String[]>of(
                new String[]{"학생", "홍길동 (8세)"}, new String[]{"언어재활센터", "평택언어이재활센터"}, new String[]{"조회 기간", "2026-09-01 ~ 2026-10-01"}),
                List.of(ReportPdfGenerator.Section.table("요약", List.of("항목", "값"), List.of(
                                List.of("평균 문장 일치도", "95%"), List.of("발음 평가 점수", "미평가"), List.of("특수문자", "100% · (괄호) & <꺾쇠> \"따옴표\"")), new float[]{2, 3}, List.of()),
                        ReportPdfGenerator.Section.table("일자별 추이", List.of("날짜", "연습 수"), List.of(), new float[]{1, 1}, List.of("선택 기간에 연습 기록이 없습니다.")),
                        ReportPdfGenerator.Section.table("음성 연습 및 선생님 검토 기록", List.of("일시", "활동 / 목표 문장", "AI 인식 결과", "문장 일치도", "선생님 판단 / 메모"), rows,
                                new float[]{1.6f, 2.4f, 2f, 1.2f, 2.6f}, List.of())),
                List.of("문장 일치도는 발음 정확도 점수가 아닙니다."));
    }

    @Test
    void embedsKoreanFontAndExtractsKoreanText() throws Exception {
        byte[] pdf = generator.generate(document(3));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("홍길동"), text);
            assertTrue(text.contains("평택언어이재활센터"));
            assertTrue(text.contains("미평가"));
            // 표 칸 안에서 줄바꿈되므로 공백·줄바꿈을 제외하고 비교한다.
            String compact = text.replaceAll("\\s+", "");
            assertTrue(compact.contains("ㄹ받침소리가약해서다음수업에서혀위치를다시연습하기로했습니다."), text);
            assertTrue(text.contains("선택 기간에 연습 기록이 없습니다."));
            assertTrue(text.contains("(괄호) & <꺾쇠>"));
            assertFalse(text.contains("???"));
            PDFont font = doc.getPage(0).getResources().getFont(doc.getPage(0).getResources().getFontNames().iterator().next());
            assertTrue(font.isEmbedded(), "한글 글꼴이 PDF에 임베딩되어야 한다");
            Path preview = Path.of("build", "reports", "pdf-preview");
            Files.createDirectories(preview);
            ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0, 72), "png", preview.resolve("report-page1.png").toFile());
            Files.write(preview.resolve("report.pdf"), pdf);
        }
    }

    @Test
    void longContentFlowsOntoMultiplePagesWithPageNumbers() throws Exception {
        try (PDDocument doc = Loader.loadPDF(generator.generate(document(60)))) {
            assertTrue(doc.getNumberOfPages() >= 3, "pages=" + doc.getNumberOfPages());
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("1 / " + doc.getNumberOfPages()));
            assertTrue(text.contains(doc.getNumberOfPages() + " / " + doc.getNumberOfPages()));
            // 빈 페이지가 없어야 한다(모든 페이지에 본문이 있다).
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                stripper.setStartPage(page); stripper.setEndPage(page);
                assertTrue(stripper.getText(doc).replaceAll("채터랜드 학습 리포트\\s+·\\s+\\d+ / \\d+", "").trim().length() > 20, "빈 페이지: " + page);
            }
        }
    }
}
