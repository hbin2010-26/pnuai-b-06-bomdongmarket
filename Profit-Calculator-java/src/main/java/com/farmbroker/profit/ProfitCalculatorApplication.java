package com.farmbroker.profit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class ProfitCalculatorApplication {
    public static void main(String[] args) throws IOException {
        Arguments arguments = Arguments.parse(args);
        Path dataDir = Path.of("src", "main", "resources", "data");
        Path outputPath = Path.of("output", "result.json");
        ProfitabilityService service = ProfitabilityService.fromDataDirectory(dataDir);

        int spaceId = arguments.spaceId();
        String cropCode = arguments.cropCode();
        if (spaceId == 0 || cropCode == null) {
            DataTables tables = CsvTableLoader.load(dataDir);
            Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
            if (spaceId == 0) {
                spaceId = selectByNumber(tables.spaces(), "사용할 공실을 선택하세요.", "space_name", scanner).intValue("space_id");
            }
            if (cropCode == null) {
                cropCode = selectByNumber(tables.crops(), "재배할 작물을 선택하세요.", "crop_name", scanner).get("crop_code");
            }
        }

        Map<String, Object> result = service.buildResult(spaceId, cropCode);
        String json = JsonUtil.toJson(result);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, json, StandardCharsets.UTF_8);
        System.out.println(json);
    }

    private static CsvRow selectByNumber(List<CsvRow> rows, String title, String labelField, Scanner scanner) {
        System.out.println();
        System.out.println(title);
        for (int i = 0; i < rows.size(); i++) {
            System.out.println((i + 1) + ". " + rows.get(i).get(labelField));
        }
        System.out.print("번호: ");
        String rawChoice = scanner.nextLine().trim();
        int choice;
        try {
            choice = Integer.parseInt(rawChoice);
        } catch (NumberFormatException exc) {
            throw new IllegalArgumentException("번호는 숫자로 입력해야 합니다.", exc);
        }
        if (choice < 1 || choice > rows.size()) {
            throw new IllegalArgumentException("번호는 1부터 " + rows.size() + " 사이여야 합니다.");
        }
        return rows.get(choice - 1);
    }

    private record Arguments(int spaceId, String cropCode) {
        static Arguments parse(String[] args) {
            int spaceId = 0;
            String cropCode = null;
            for (int i = 0; i < args.length; i++) {
                if ("--space-id".equals(args[i]) && i + 1 < args.length) {
                    spaceId = Integer.parseInt(args[++i]);
                } else if ("--crop-code".equals(args[i]) && i + 1 < args.length) {
                    cropCode = args[++i];
                }
            }
            return new Arguments(spaceId, cropCode);
        }
    }
}

