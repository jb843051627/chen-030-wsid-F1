package com.fc.v2.model.custom;

import java.util.List;

/**
 * 倒核结果：行面去处与单面三笔互相圆得上才算齐。
 *
 * <p>三笔一律按行表重算（{@code recounted*}），再与单面刻档（{@code receipt*}）比对；
 * 另随机抽一行倒着核（{@link #checkedRowNo}）。对不齐的行按行次点名列在 {@link #diffRows}，
 * 不许用「大致相符」一类说法。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
public class WsidReconcileVo {

    /** 串码 */
    private String batchNo;

    /** 单面刻档：进门落定笔数 */
    private int receiptEnterCount;
    /** 单面刻档：收下笔数 */
    private int receiptAcceptedCount;
    /** 单面刻档：退回笔数 */
    private int receiptRejectedCount;

    /** 行表重算：进门落定笔数 */
    private int recountedEnterCount;
    /** 行表重算：收下笔数 */
    private int recountedAcceptedCount;
    /** 行表重算：退回笔数 */
    private int recountedRejectedCount;

    /** 随机抽中倒核的那一行次（无落定行时为 null） */
    private Integer checkedRowNo;
    /** 抽中那一行的去处 0未勾 1收下 2退回 */
    private Integer checkedStatus;
    /** 抽中那一行倒核是否圆得上 */
    private boolean checkedRowSound;

    /** 三笔与逐行是否整体圆得上 */
    private boolean consistent;

    /** 对不齐的行，逐行点名（行次＋缘由） */
    private List<DiffRow> diffRows;

    /** 一行一条不符 */
    public static class DiffRow {
        private final Integer rowNo;
        private final Integer status;
        private final String message;

        public DiffRow(Integer rowNo, Integer status, String message) {
            this.rowNo = rowNo;
            this.status = status;
            this.message = message;
        }

        public Integer getRowNo() {
            return rowNo;
        }

        public Integer getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public int getReceiptEnterCount() {
        return receiptEnterCount;
    }

    public void setReceiptEnterCount(int receiptEnterCount) {
        this.receiptEnterCount = receiptEnterCount;
    }

    public int getReceiptAcceptedCount() {
        return receiptAcceptedCount;
    }

    public void setReceiptAcceptedCount(int receiptAcceptedCount) {
        this.receiptAcceptedCount = receiptAcceptedCount;
    }

    public int getReceiptRejectedCount() {
        return receiptRejectedCount;
    }

    public void setReceiptRejectedCount(int receiptRejectedCount) {
        this.receiptRejectedCount = receiptRejectedCount;
    }

    public int getRecountedEnterCount() {
        return recountedEnterCount;
    }

    public void setRecountedEnterCount(int recountedEnterCount) {
        this.recountedEnterCount = recountedEnterCount;
    }

    public int getRecountedAcceptedCount() {
        return recountedAcceptedCount;
    }

    public void setRecountedAcceptedCount(int recountedAcceptedCount) {
        this.recountedAcceptedCount = recountedAcceptedCount;
    }

    public int getRecountedRejectedCount() {
        return recountedRejectedCount;
    }

    public void setRecountedRejectedCount(int recountedRejectedCount) {
        this.recountedRejectedCount = recountedRejectedCount;
    }

    public Integer getCheckedRowNo() {
        return checkedRowNo;
    }

    public void setCheckedRowNo(Integer checkedRowNo) {
        this.checkedRowNo = checkedRowNo;
    }

    public Integer getCheckedStatus() {
        return checkedStatus;
    }

    public void setCheckedStatus(Integer checkedStatus) {
        this.checkedStatus = checkedStatus;
    }

    public boolean isCheckedRowSound() {
        return checkedRowSound;
    }

    public void setCheckedRowSound(boolean checkedRowSound) {
        this.checkedRowSound = checkedRowSound;
    }

    public boolean isConsistent() {
        return consistent;
    }

    public void setConsistent(boolean consistent) {
        this.consistent = consistent;
    }

    public List<DiffRow> getDiffRows() {
        return diffRows;
    }

    public void setDiffRows(List<DiffRow> diffRows) {
        this.diffRows = diffRows;
    }
}
