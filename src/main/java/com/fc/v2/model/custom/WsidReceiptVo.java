package com.fc.v2.model.custom;

import java.util.List;

import com.fc.v2.model.auto.TWsidApplyRow;

/**
 * 一串进门后写出的收讫单面（读这一个为准，不看页面抢先亮出的数）。
 *
 * <p>末尾三笔：进门 {@link #enterCount}／收下 {@link #acceptedCount}／退回 {@link #rejectedCount}，
 *  settled 之后后两笔相加必等于头一笔；三个数都取自「逐行定去处」那一趟。
 *  撞上限余下的行记在 {@link #overflowCount}，另起一串去接，不计入三笔。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
public class WsidReceiptVo {

    /** 空册：一行没进门，单面只落「空册」，不出收讫结论 */
    public static final int KIND_EMPTY = 0;
    /** 已收讫：逐行定去处那一趟跑完，三笔圆得上 */
    public static final int KIND_RECEIVED = 1;
    /** 只对照：同一串第二遍到（另一来路），头一遍账不动，另留一份单面 */
    public static final int KIND_COMPARED = 2;

    /** 站所自录 */
    public static final String PUT_STATION = "站所自录";
    /** 县局代收 */
    public static final String PUT_COUNTY = "县局代收";

    /** 串码 */
    private String batchNo;

    /** 本份单面的来路 */
    private String putKind;

    /** 册头原样写定的上限（空着为 null，刻档备查） */
    private Integer declaredLimit;

    /** 实际认下的上限（空／负数／没边大数归一为 150） */
    private int effectiveLimit;

    /** 点行笔数：册子一到点出的行数，连同上限原样刻档（隔半年查得回当初报了几行） */
    private int reportedCount;

    /** 进门落定笔数：逐行定去处后收下＋退回之和（空册为 0） */
    private int enterCount;

    /** 未勾待续笔数：两个数缺一、这一行不办，落未勾等续办 */
    private int pendingCount;

    /** 收下笔数 */
    private int acceptedCount;

    /** 退回笔数 */
    private int rejectedCount;

    /** 撞上限余下、须另起一串去接的行数 */
    private int overflowCount;

    /** 单面情形：0空册 1已收讫 2只对照 */
    private int kind;

    /** 对照不符的行次（第二遍只对照时才可能有；账不动） */
    private List<Integer> compareDiffRowNos;

    /** 单面记要 */
    private String content;

    /** 本串逐行去处（行次升序，每行带退回缘由，事后逐行问得出、答得上） */
    private List<TWsidApplyRow> rows;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getPutKind() {
        return putKind;
    }

    public void setPutKind(String putKind) {
        this.putKind = putKind;
    }

    public Integer getDeclaredLimit() {
        return declaredLimit;
    }

    public void setDeclaredLimit(Integer declaredLimit) {
        this.declaredLimit = declaredLimit;
    }

    public int getEffectiveLimit() {
        return effectiveLimit;
    }

    public void setEffectiveLimit(int effectiveLimit) {
        this.effectiveLimit = effectiveLimit;
    }

    public int getReportedCount() {
        return reportedCount;
    }

    public void setReportedCount(int reportedCount) {
        this.reportedCount = reportedCount;
    }

    public int getEnterCount() {
        return enterCount;
    }

    public void setEnterCount(int enterCount) {
        this.enterCount = enterCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }

    public int getAcceptedCount() {
        return acceptedCount;
    }

    public void setAcceptedCount(int acceptedCount) {
        this.acceptedCount = acceptedCount;
    }

    public int getRejectedCount() {
        return rejectedCount;
    }

    public void setRejectedCount(int rejectedCount) {
        this.rejectedCount = rejectedCount;
    }

    public int getOverflowCount() {
        return overflowCount;
    }

    public void setOverflowCount(int overflowCount) {
        this.overflowCount = overflowCount;
    }

    public int getKind() {
        return kind;
    }

    public void setKind(int kind) {
        this.kind = kind;
    }

    public List<Integer> getCompareDiffRowNos() {
        return compareDiffRowNos;
    }

    public void setCompareDiffRowNos(List<Integer> compareDiffRowNos) {
        this.compareDiffRowNos = compareDiffRowNos;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<TWsidApplyRow> getRows() {
        return rows;
    }

    public void setRows(List<TWsidApplyRow> rows) {
        this.rows = rows;
    }
}
