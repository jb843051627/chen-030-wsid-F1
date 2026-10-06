package com.fc.v2.model.custom;

import java.math.BigDecimal;

/**
 * 进门一行的册面输入：一行对一个主体，两个数（申领张数 qty、册面对上的张数 chkNum）。
 * 行次由系统在本串里排，不留手填的格子——这里没有 rowNo 入口。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class WsidApplyLineDto {

    /** 被对上的繁育主体册代号（科里校验规则里要解析的名册栏之一） */
    private String itemCode;

    /** 本行申领张数（少了这一行不办，系统不代填、不按零认下） */
    private BigDecimal qty;

    /** 与册面核对得上的张数（少了这一行不办） */
    private BigDecimal chkNum;

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getChkNum() {
        return chkNum;
    }

    public void setChkNum(BigDecimal chkNum) {
        this.chkNum = chkNum;
    }
}
