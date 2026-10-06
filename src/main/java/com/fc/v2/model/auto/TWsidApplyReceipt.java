package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 标识申领名册收讫单面对象 t_wsid_apply_receipt
 *
 * <p>一串一份；同一串走两回（站所自录／县局代收）各留一份，靠 (batch_no, put_kind) 区分。
 * 点行数与册头写定的上限进门即刻档，隔半年仍查得回当初报了几行。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
@TableName("t_wsid_apply_receipt")
@ApiModel(value = "TWsidApplyReceipt", description = "标识申领名册收讫单面")
public class TWsidApplyReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 申领名册核收批次码（哪一串） */
    @TableField("batch_no")
    @ApiModelProperty(value = "申领名册核收批次码（哪一串）")
    private String batchNo;

    /** 来路（站所自录／县局代收两条来路，第二遍照此另立一份） */
    @TableField("put_kind")
    @ApiModelProperty(value = "来路（站所自录／县局代收两条来路，第二遍照此另立一份）")
    private String putKind;

    /** 册头写定的上限（原样刻档：空/负数/没边大数归一为 150） */
    @TableField("declared_limit")
    @ApiModelProperty(value = "册头写定的上限（原样刻档：空/负数/没边大数归一为 150）")
    private Integer declaredLimit;

    /** 实际认下的上限（缺省 150） */
    @TableField("effective_limit")
    @ApiModelProperty(value = "实际认下的上限（缺省 150）")
    private Integer effectiveLimit;

    /** 点行所得（当初报了几行，原样刻档，隔半年查得回） */
    @TableField("reported_count")
    @ApiModelProperty(value = "点行所得（当初报了几行，原样刻档，隔半年查得回）")
    private Integer reportedCount;

    /** 进门落定笔数（收下+退回，恒等于后两笔之和） */
    @TableField("enter_count")
    @ApiModelProperty(value = "进门落定笔数（收下+退回，恒等于后两笔之和）")
    private Integer enterCount;

    /** 未勾待续笔数（两数缺一、本行不办，待续办补上） */
    @TableField("pending_count")
    @ApiModelProperty(value = "未勾待续笔数（两数缺一、本行不办，待续办补上）")
    private Integer pendingCount;

    /** 收下笔数（取自逐行定去处那一趟） */
    @TableField("accepted_count")
    @ApiModelProperty(value = "收下笔数（取自逐行定去处那一趟）")
    private Integer acceptedCount;

    /** 退回笔数（取自逐行定去处那一趟） */
    @TableField("rejected_count")
    @ApiModelProperty(value = "退回笔数（取自逐行定去处那一趟）")
    private Integer rejectedCount;

    /** 撞上限后余下另起一串的行数 */
    @TableField("overflow_count")
    @ApiModelProperty(value = "撞上限后余下另起一串的行数")
    private Integer overflowCount;

    /** 单面情形 0空册 1已收讫 2只对照(第二遍) 3已回撤 */
    @TableField("receipt_kind")
    @ApiModelProperty(value = "单面情形 0空册 1已收讫 2只对照(第二遍) 3已回撤")
    private Integer receiptKind;

    /** 本串记要（空册字样／对照结论／回撤一截） */
    @TableField("content")
    @ApiModelProperty(value = "本串记要（空册字样／对照结论／回撤一截）")
    private String content;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Integer getEffectiveLimit() {
        return effectiveLimit;
    }

    public void setEffectiveLimit(Integer effectiveLimit) {
        this.effectiveLimit = effectiveLimit;
    }

    public Integer getEnterCount() {
        return enterCount;
    }

    public void setEnterCount(Integer enterCount) {
        this.enterCount = enterCount;
    }

    public Integer getReportedCount() {
        return reportedCount;
    }

    public void setReportedCount(Integer reportedCount) {
        this.reportedCount = reportedCount;
    }

    public Integer getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(Integer pendingCount) {
        this.pendingCount = pendingCount;
    }

    public Integer getAcceptedCount() {
        return acceptedCount;
    }

    public void setAcceptedCount(Integer acceptedCount) {
        this.acceptedCount = acceptedCount;
    }

    public Integer getRejectedCount() {
        return rejectedCount;
    }

    public void setRejectedCount(Integer rejectedCount) {
        this.rejectedCount = rejectedCount;
    }

    public Integer getOverflowCount() {
        return overflowCount;
    }

    public void setOverflowCount(Integer overflowCount) {
        this.overflowCount = overflowCount;
    }

    public Integer getReceiptKind() {
        return receiptKind;
    }

    public void setReceiptKind(Integer receiptKind) {
        this.receiptKind = receiptKind;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
