package com.netflix.eureka2.testkit.cli.command.session;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple table data formatter.
 *
 * @author Tomasz Bak
 */
public class TableFormatter {

    private static final int MIN_COLUMN_LENGTH = 10;

    private final List<String> headers;
    private final int[] columnLength;

    private boolean printedHeader;

    public TableFormatter(Collection<String> headers) {
        this.headers = new ArrayList<>(headers);
        this.columnLength = new int[headers.size()];
    }

    public void format(PrintStream out, Map<String, Object> dataRow) {
        if (!printedHeader) {
            int totalLength = 0;
            out.println();
            for (int i = 0; i < columnLength.length; i++) {
                String key = headers.get(i);
                if (dataRow.containsKey(key)) {
                    columnLength[i] = Math.max(MIN_COLUMN_LENGTH, Math.max(key.length(), dataRow.get(key).toString().length()));
                    out.printf(" %" + columnLength[i] + "s |", key);
                } else {
                    columnLength[i] = Math.min(MIN_COLUMN_LENGTH, key.length());
                    out.printf(" %" + columnLength[i] + "s |", "");
                }
                totalLength += columnLength[i] + 3;
            }
            out.println();
            for (int i = 0; i < totalLength; i++) {
                out.print('-');
            }
            out.println();
            printedHeader = true;
        }
        for (int i = 0; i < columnLength.length; i++) {
            String key = headers.get(i);
            if (dataRow.containsKey(key)) {
                columnLength[i] = Math.max(columnLength[i], dataRow.get(key).toString().length());
                out.printf(" %" + columnLength[i] + "s |", dataRow.get(key));
            } else {
                out.printf(" %" + columnLength[i] + "s |", "");
            }
        }
        out.println();
    }

    public static void main(String[] args) {
        TableFormatter formatter = new TableFormatter(Arrays.asList("id", "valueA", "valueB"));
        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", 1);
        row1.put("valueA", "aaa");
        row1.put("valueB", "bbb");
        formatter.format(System.out, row1);
        Map<String, Object> row2 = new HashMap<>();
        row2.put("id", 123);
        row2.put("valueA", "aaaAAA");
        row2.put("valueB", "bbbBBB");
        formatter.format(System.out, row2);
    }
}
