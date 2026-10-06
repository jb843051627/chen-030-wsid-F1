package com.fc.v2.model.custom;

import java.util.List;

/**
 * 回撤结果：只有还没定下来（未勾）的行摆得回未勾，已经落定（收下／退回）的一截一行也不跟着回。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class WsidRollbackVo {

    /** 串码 */
    private String batchNo;

    /** 摆回未勾的行次（回撤前仍未定的那几行） */
    private List<Integer> resetRowNos;

    /** 已落定、纹丝不动的行次（收下／退回） */
    private List<Integer> settledUntouchedRowNos;

    /** 回撤后单面三笔：进门（落定+未勾）／收下／退回 */
    private int enterCount;
    private int acceptedCount;
    private int rejectedCount;

    /** 单面记要 */
    private String content;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public List<Integer> getResetRowNos() {
        return resetRowNos;
    }

    public void setResetRowNos(List<Integer> resetRowNos) {
        this.resetRowNos = resetRowNos;
    }

    public List<Integer> getSettledUntouchedRowNos() {
        return settledUntouchedRowNos;
    }

    public void setSettledUntouchedRowNos(List<Integer> settledUntouchedRowNos) {
        this.settledUntouchedRowNos = settledUntouchedRowNos;
    }

    public int getEnterCount() {
        return enterCount;
    }

    public void setEnterCount(int enterCount) {
        this.enterCount = enterCount;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
