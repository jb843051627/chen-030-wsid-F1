package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TWsidApplyReceiptMapper;
import com.fc.v2.mapper.auto.TWsidApplyRowMapper;
import com.fc.v2.model.auto.TWsidApplyReceipt;
import com.fc.v2.model.auto.TWsidApplyRow;
import com.fc.v2.model.custom.WsidApplyLineDto;
import com.fc.v2.model.custom.WsidReceiptVo;
import com.fc.v2.model.custom.WsidReconcileVo;
import com.fc.v2.model.custom.WsidRollbackVo;
import com.fc.v2.service.ITWsidApplyRowService;

/**
 * 标识申领名册核收行 Service业务层处理。
 *
 * <p>整串进门四步连着办（见 {@link #receive}）；去处只三样 0未勾／1收下／2退回，
 * 第四样存不进表里。行次由系统在本串里排，同串再报沿用，不重新起步。
 * 行面（t_wsid_apply_row）与单面（t_wsid_apply_receipt）凡有改动必在同一事务里两处一起动。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TWsidApplyRowServiceImpl implements ITWsidApplyRowService {

    /** 册头空着／写负数／写没边大数时，一律按 150 行认；上限只此一处，别处不设第二道 */
    public static final int DEFAULT_LIMIT = 150;
    /** 没边大数的 sanity 线：超过这个数视同没填 */
    public static final int LIMIT_SANITY = 10000;

    /** 行落地情形：未勾（两数缺一、这一行不办，待续办） */
    public static final int STATUS_PENDING = 0;
    /** 行落地情形：收下 */
    public static final int STATUS_ACCEPTED = 1;
    /** 行落地情形：退回（缘由逐行写明） */
    public static final int STATUS_REJECTED = 2;

    /** 单面情形：空册（一行没进门，不出收讫结论） */
    public static final int RECEIPT_EMPTY = 0;
    /** 单面情形：已收讫 */
    public static final int RECEIPT_RECEIVED = 1;
    /** 单面情形：只对照（第二遍到，账不动） */
    public static final int RECEIPT_COMPARED = 2;
    /** 单面情形：已回撤 */
    public static final int RECEIPT_ROLLED_BACK = 3;

    @javax.annotation.Resource
    private TWsidApplyRowMapper wsidApplyRowMapper;

    @javax.annotation.Resource
    private TWsidApplyReceiptMapper wsidApplyReceiptMapper;

    /** 一行的去处判定：status 只可能是 0未勾／1收下／2退回；rejectReason 仅退回时有字 */
    private static final class Verdict {
        final int status;
        final String reason;

        Verdict(int status, String reason) {
            this.status = status;
            this.reason = reason;
        }
    }

    @Override
    public TWsidApplyRow selectTWsidApplyRowById(Long id) {
        return this.wsidApplyRowMapper.selectById(id);
    }

    @Override
    public List<TWsidApplyRow> listRows(String batchNo) {
        return listRowsOrdered(batchNo);
    }

    @Override
    public TWsidApplyReceipt getReceipt(String batchNo, String putKind) {
        return this.wsidApplyReceiptMapper.selectOne(new QueryWrapper<TWsidApplyReceipt>()
                .eq("batch_no", batchNo)
                .eq("put_kind", putKind));
    }

    /**
     * 整串进门。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WsidReceiptVo receive(String batchNo, String putKind, Integer declaredLimit, List<WsidApplyLineDto> lines) {
        checkBatchNo(batchNo);
        checkPutKind(putKind);
        List<WsidApplyLineDto> safeLines = lines == null ? Collections.<WsidApplyLineDto>emptyList() : lines;

        int effectiveLimit = normalizeLimit(declaredLimit);

        // 第一步：点行数（册子一到先点行；点出的数连同写定上限即刻刻进单面，隔半年查得回）
        int reported = safeLines.size();

        // 同一串第二遍到：只对照不改账。同来路重复递交也不许把记号翻回去重走。
        TWsidApplyReceipt samePass = getReceipt(batchNo, putKind);
        if (samePass != null) {
            return compareOnly(safeLines, samePass);
        }
        // 另一来路（站所自录／县局代收）的第二份单面：只对照，账与头一遍刻档（点行/上限/溢出）都照头一份
        TWsidApplyReceipt firstPass = firstReceipt(batchNo);
        if (firstPass != null) {
            List<Integer> diffs = diffAgainstRows(firstPass, safeLines);
            TWsidApplyReceipt receipt = newReceipt(batchNo, putKind,
                    firstPass.getDeclaredLimit(), nz(firstPass.getEffectiveLimit()));
            receipt.setReportedCount(nz(firstPass.getReportedCount()));
            receipt.setOverflowCount(nz(firstPass.getOverflowCount()));
            int[] cnt = recount(batchNo);
            receipt.setEnterCount(cnt[0]);
            receipt.setPendingCount(cnt[1]);
            receipt.setAcceptedCount(cnt[2]);
            receipt.setRejectedCount(cnt[3]);
            receipt.setReceiptKind(RECEIPT_COMPARED);
            receipt.setContent("只对照不改账：头一遍定下来的行不回未勾、不另立；不符行次 "
                    + (diffs.isEmpty() ? "无" : abbrev(diffs)));
            this.wsidApplyReceiptMapper.insert(receipt);
            return toVo(receipt, listRowsOrdered(batchNo), diffs);
        }

        TWsidApplyReceipt receipt = newReceipt(batchNo, putKind, declaredLimit, effectiveLimit);
        receipt.setReportedCount(reported);

        // 连一行都没够着的空串是正经输入：单面只落「空册」，收讫那一句不出，也不从别串拉行填数
        if (reported == 0) {
            receipt.setEnterCount(0);
            receipt.setPendingCount(0);
            receipt.setAcceptedCount(0);
            receipt.setRejectedCount(0);
            receipt.setOverflowCount(0);
            receipt.setReceiptKind(RECEIPT_EMPTY);
            receipt.setContent("空册");
            this.wsidApplyReceiptMapper.insert(receipt);
            return toVo(receipt, Collections.<TWsidApplyRow>emptyList(), null);
        }

        // 撞上限就收手，余下的另起一串去接，本串不许自行往里添
        int admitted = Math.min(reported, effectiveLimit);
        int overflow = reported - admitted;
        receipt.setOverflowCount(overflow);

        int accepted = 0;
        int rejected = 0;
        int pending = 0;
        List<TWsidApplyRow> stored = new ArrayList<TWsidApplyRow>(admitted);

        // 行次由系统在本串里排（1 起），没有敲键的地方；一家主体占两行各定各的去处，谁也吞不掉谁
        for (int i = 0; i < admitted; i++) {
            WsidApplyLineDto line = safeLines.get(i);
            int rowNo = i + 1;
            TWsidApplyRow row = new TWsidApplyRow();
            row.setBatchNo(batchNo);
            row.setRowNo(rowNo);
            row.setPutKind(putKind);
            row.setItemCode(line == null ? null : line.getItemCode());
            row.setQty(line == null ? null : line.getQty());
            row.setChkNum(line == null ? null : line.getChkNum());
            row.setDelFlag(0);

            // 第二、三步连着办：取行上两个数，按科里校验规则逐行解析；末了给每行落一个去处
            Verdict verdict = judge(line);
            row.setStatus(verdict.status);
            row.setRejectReason(verdict.reason);
            this.wsidApplyRowMapper.insert(row);
            stored.add(row);
            if (verdict.status == STATUS_ACCEPTED) {
                accepted++;
            } else if (verdict.status == STATUS_REJECTED) {
                rejected++;
            } else {
                pending++;
            }
        }

        // 单面末尾三笔都取自「逐行定去处」那一趟，后两笔相加等于头一笔（进门落定笔数）
        receipt.setEnterCount(accepted + rejected);
        receipt.setPendingCount(pending);
        receipt.setAcceptedCount(accepted);
        receipt.setRejectedCount(rejected);
        receipt.setReceiptKind(RECEIPT_RECEIVED);
        receipt.setContent("收讫：点行" + reported + "，收下" + accepted + "、退回" + rejected
                + "、未勾" + pending + (overflow > 0 ? "，余" + overflow + "行另起一串" : ""));
        this.wsidApplyReceiptMapper.insert(receipt);
        return toVo(receipt, stored, null);
    }

    /**
     * 中途停过一回后续办：只补没定下来的那几行，已经定下的那一段不受惊动。
     *
     * @param fills 行次 → 补报的那一栏（只许对准未勾行；行次认当初排定的，不重新起步）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WsidReceiptVo resume(String batchNo, Map<Integer, WsidApplyLineDto> fills) {
        checkBatchNo(batchNo);
        TWsidApplyReceipt receipt = accountingReceipt(batchNo);
        if (receipt == null) {
            throw new IllegalStateException("本串尚无收讫单面，无从续办：" + batchNo);
        }
        Map<Integer, TWsidApplyRow> rowIndex = indexRows(batchNo);
        Map<Integer, WsidApplyLineDto> safeFills = fills == null
                ? Collections.<Integer, WsidApplyLineDto>emptyMap() : fills;
        for (Map.Entry<Integer, WsidApplyLineDto> e : safeFills.entrySet()) {
            TWsidApplyRow row = rowIndex.get(e.getKey());
            if (row == null) {
                throw new IllegalArgumentException("第" + e.getKey() + "行不在本串，续办不许往别串拉行");
            }
            if (row.getStatus() != null && row.getStatus() != STATUS_PENDING) {
                throw new IllegalStateException("第" + e.getKey() + "行已定下来，续办不受惊动");
            }
            settleRow(row, e.getValue());
            this.wsidApplyRowMapper.updateById(row);
        }
        return refreshAccountingReceipt(receipt, "续办补定", RECEIPT_RECEIVED);
    }

    /**
     * 吃了退回的那一行过几天再报：认的还是当初排定的那一个行次，不重新起步、不飘到别串。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WsidReceiptVo recast(String batchNo, Integer rowNo, WsidApplyLineDto line) {
        checkBatchNo(batchNo);
        if (rowNo == null) {
            throw new IllegalArgumentException("再报须指当初排定的行次");
        }
        TWsidApplyReceipt receipt = accountingReceipt(batchNo);
        if (receipt == null) {
            throw new IllegalStateException("本串尚无收讫单面，无从再报：" + batchNo);
        }
        TWsidApplyRow row = this.wsidApplyRowMapper.selectOne(new QueryWrapper<TWsidApplyRow>()
                .eq("batch_no", batchNo)
                .eq("row_no", rowNo));
        if (row == null) {
            throw new IllegalArgumentException("第" + rowNo + "行不在本串，行不会飘到别串去");
        }
        if (row.getStatus() != null && row.getStatus() == STATUS_ACCEPTED) {
            throw new IllegalStateException("第" + rowNo + "行先前已收下，不收第二回、不回未勾");
        }
        if (row.getStatus() == null || row.getStatus() != STATUS_REJECTED) {
            throw new IllegalStateException("第" + rowNo + "行不是退回行，再报只接退回的那几行");
        }
        settleRow(row, line);
        this.wsidApplyRowMapper.updateById(row);
        return refreshAccountingReceipt(receipt, "第" + rowNo + "行退回后再报", RECEIPT_RECEIVED);
    }

    /**
     * 往回撤：只有还没定下来（未勾）的那几行摆得回未勾，已经落定的一截一行也不跟着回来。
     * 行面与单面同一事务两处一起动；没有未勾行时无物可撤，原样返回 null。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WsidRollbackVo rollback(String batchNo) {
        checkBatchNo(batchNo);
        TWsidApplyReceipt receipt = accountingReceipt(batchNo);
        if (receipt == null) {
            throw new IllegalStateException("本串尚无收讫单面，无从回撤：" + batchNo);
        }
        List<TWsidApplyRow> rows = listRowsOrdered(batchNo);
        List<Integer> reset = new ArrayList<Integer>();
        List<Integer> untouched = new ArrayList<Integer>();
        for (TWsidApplyRow row : rows) {
            int st = row.getStatus() == null ? STATUS_PENDING : row.getStatus();
            if (st == STATUS_PENDING) {
                // 行面这一处：未勾行明确摆回未勾（不删行、不动落定行）
                row.setStatus(STATUS_PENDING);
                row.setRejectReason(null);
                row.setRemark("回撤摆回未勾");
                this.wsidApplyRowMapper.updateById(row);
                reset.add(row.getRowNo());
            } else {
                untouched.add(row.getRowNo());
            }
        }
        if (reset.isEmpty()) {
            // 已经落定的一截一行也不跟着回来：无未勾行即无行面可动，不做单面残改动
            return null;
        }

        int[] cnt = recount(batchNo);
        receipt.setEnterCount(cnt[0]);
        receipt.setPendingCount(cnt[1]);
        receipt.setAcceptedCount(cnt[2]);
        receipt.setRejectedCount(cnt[3]);
        receipt.setReceiptKind(RECEIPT_ROLLED_BACK);
        receipt.setContent("已回撤：第" + abbrev(reset) + "行摆回未勾；收下／退回那一截（行次"
                + abbrev(untouched) + "）一行不动");
        this.wsidApplyReceiptMapper.updateById(receipt);

        WsidRollbackVo vo = new WsidRollbackVo();
        vo.setBatchNo(batchNo);
        vo.setResetRowNos(reset);
        vo.setSettledUntouchedRowNos(untouched);
        vo.setEnterCount(cnt[0]);
        vo.setAcceptedCount(cnt[2]);
        vo.setRejectedCount(cnt[3]);
        vo.setContent(receipt.getContent());
        return vo;
    }

    /**
     * 倒核：三笔全部按行表重算，与单面刻档比；随机抽一行倒着核；不符按行次点名。
     */
    @Override
    public WsidReconcileVo reconcile(String batchNo) {
        checkBatchNo(batchNo);
        TWsidApplyReceipt receipt = accountingReceipt(batchNo);
        if (receipt == null) {
            throw new IllegalStateException("本串尚无收讫单面，无从倒核：" + batchNo);
        }
        List<TWsidApplyRow> rows = listRowsOrdered(batchNo);
        int[] cnt = recount(batchNo);

        WsidReconcileVo vo = new WsidReconcileVo();
        vo.setBatchNo(batchNo);
        vo.setReceiptEnterCount(nz(receipt.getEnterCount()));
        vo.setReceiptAcceptedCount(nz(receipt.getAcceptedCount()));
        vo.setReceiptRejectedCount(nz(receipt.getRejectedCount()));
        vo.setRecountedEnterCount(cnt[0]);
        vo.setRecountedAcceptedCount(cnt[2]);
        vo.setRecountedRejectedCount(cnt[3]);

        // 逐行点名：去处只许三样；收下的两数必相等；退回必带缘由；两数齐全且相等的不该挂未勾
        List<WsidReconcileVo.DiffRow> diffs = new ArrayList<WsidReconcileVo.DiffRow>();
        List<TWsidApplyRow> settled = new ArrayList<TWsidApplyRow>();
        for (TWsidApplyRow r : rows) {
            Integer st = r.getStatus();
            if (st == null || (st != STATUS_PENDING && st != STATUS_ACCEPTED && st != STATUS_REJECTED)) {
                diffs.add(new WsidReconcileVo.DiffRow(r.getRowNo(), st, "去处存了第四样，表里只许未勾／收下／退回"));
                continue;
            }
            if (st == STATUS_PENDING) {
                if (r.getQty() != null && r.getChkNum() != null
                        && r.getQty().compareTo(r.getChkNum()) == 0
                        && r.getQty().compareTo(BigDecimal.ZERO) > 0) {
                    diffs.add(new WsidReconcileVo.DiffRow(r.getRowNo(), st, "两数已齐且摆得平，不该还挂未勾"));
                }
            } else if (st == STATUS_ACCEPTED) {
                settled.add(r);
                if (r.getQty() == null || r.getChkNum() == null
                        || r.getQty().compareTo(r.getChkNum()) != 0) {
                    diffs.add(new WsidReconcileVo.DiffRow(r.getRowNo(), st, "记着收下，两数却对不平"));
                }
            } else {
                settled.add(r);
                if (r.getRejectReason() == null || r.getRejectReason().trim().isEmpty()) {
                    diffs.add(new WsidReconcileVo.DiffRow(r.getRowNo(), st, "退回却没写缘由，事后答不上为哪一条退"));
                }
            }
        }

        // 随机抽一行倒着核：它的去处要在重算三笔里圆得上
        if (!settled.isEmpty()) {
            TWsidApplyRow picked = settled.get(ThreadLocalRandom.current().nextInt(settled.size()));
            vo.setCheckedRowNo(picked.getRowNo());
            vo.setCheckedStatus(picked.getStatus());
            boolean sound;
            if (picked.getStatus() == STATUS_ACCEPTED) {
                sound = cnt[2] > 0 && picked.getQty() != null && picked.getQty().compareTo(picked.getChkNum()) == 0;
            } else {
                sound = cnt[3] > 0 && picked.getRejectReason() != null && !picked.getRejectReason().trim().isEmpty();
            }
            vo.setCheckedRowSound(sound);
            if (!sound) {
                diffs.add(new WsidReconcileVo.DiffRow(picked.getRowNo(), picked.getStatus(), "抽核这一行与三笔圆不上"));
            }
        }

        boolean countsSound = vo.getReceiptEnterCount() == cnt[0]
                && vo.getReceiptAcceptedCount() == cnt[2]
                && vo.getReceiptRejectedCount() == cnt[3]
                && cnt[0] == cnt[2] + cnt[3];
        vo.setConsistent(countsSound && diffs.isEmpty());
        vo.setDiffRows(diffs);
        return vo;
    }

    // ------------------------------------------------------------------

    /** 科里定下的校验规则：两个数同时落到行上才办；去处只三样，退回缘由逐行写明 */
    private Verdict judge(WsidApplyLineDto line) {
        if (line == null || line.getQty() == null || line.getChkNum() == null) {
            // 少任何一个数，这一行不办：不代填、不按零认下，落未勾等续办
            return new Verdict(STATUS_PENDING, null);
        }
        if (line.getItemCode() == null || line.getItemCode().trim().isEmpty()) {
            return new Verdict(STATUS_REJECTED, "主体册代号缺栏，咬定不到繁育主体");
        }
        BigDecimal qty = line.getQty();
        BigDecimal chk = line.getChkNum();
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            return new Verdict(STATUS_REJECTED, "申领张数不成数：" + plain(qty));
        }
        if (chk.compareTo(BigDecimal.ZERO) < 0) {
            return new Verdict(STATUS_REJECTED, "核对张数不成数：" + plain(chk));
        }
        BigDecimal diff = qty.subtract(chk);
        int cmp = diff.compareTo(BigDecimal.ZERO);
        if (cmp > 0) {
            // 第 7 行报 20、对上 18，退就退在「差 2 张」这一句上
            return new Verdict(STATUS_REJECTED, "差 " + plain(diff) + " 张");
        }
        if (cmp < 0) {
            return new Verdict(STATUS_REJECTED, "多 " + plain(diff.negate()) + " 张");
        }
        return new Verdict(STATUS_ACCEPTED, null);
    }

    /** 续办／再报时把新一栏落到原行（行次不变），重新判去处 */
    private void settleRow(TWsidApplyRow row, WsidApplyLineDto line) {
        if (line == null) {
            throw new IllegalArgumentException("第" + row.getRowNo() + "行补报为空，两个数仍缺着");
        }
        Verdict verdict = judge(line);
        if (verdict.status == STATUS_PENDING) {
            throw new IllegalArgumentException("第" + row.getRowNo() + "行两个数仍未同时落上，系统不代填、不按零认下");
        }
        row.setItemCode(line.getItemCode());
        row.setQty(line.getQty());
        row.setChkNum(line.getChkNum());
        row.setStatus(verdict.status);
        row.setRejectReason(verdict.reason);
    }

    /**
     * 第二遍到：只对照不改账，照头一份单面回话（同来路重复递交也走这里，绝不重走记号）。
     */
    private WsidReceiptVo compareOnly(List<WsidApplyLineDto> incoming, TWsidApplyReceipt existing) {
        List<Integer> diffs = diffAgainstRows(existing, incoming);
        WsidReceiptVo vo = toVo(existing, listRowsOrdered(existing.getBatchNo()), diffs);
        vo.setKind(RECEIPT_COMPARED);
        vo.setContent("重复进门只对照：头一遍账一字不动、不另立一处；不符行次 "
                + (diffs.isEmpty() ? "无" : abbrev(diffs)));
        return vo;
    }

    /** 把进门的一串与账上逐行对：行次对不上、两数／代号对不上都点名 */
    private List<Integer> diffAgainstRows(TWsidApplyReceipt anchor, List<WsidApplyLineDto> incoming) {
        List<TWsidApplyRow> rows = listRowsOrdered(anchor.getBatchNo());
        int admitted = Math.min(incoming.size(), anchor.getEffectiveLimit() == null
                ? DEFAULT_LIMIT : anchor.getEffectiveLimit());
        Map<Integer, TWsidApplyRow> index = new LinkedHashMap<Integer, TWsidApplyRow>();
        int maxRowNo = 0;
        for (TWsidApplyRow r : rows) {
            index.put(r.getRowNo(), r);
            maxRowNo = Math.max(maxRowNo, r.getRowNo() == null ? 0 : r.getRowNo());
        }
        List<Integer> diffs = new ArrayList<Integer>();
        for (int i = 0; i < Math.max(admitted, maxRowNo); i++) {
            int rowNo = i + 1;
            TWsidApplyRow r = index.get(rowNo);
            WsidApplyLineDto l = i < admitted ? incoming.get(i) : null;
            if (r == null || l == null
                    || !eq(r.getItemCode(), l.getItemCode())
                    || !numEq(r.getQty(), l.getQty())
                    || !numEq(r.getChkNum(), l.getChkNum())) {
                diffs.add(rowNo);
            }
        }
        return diffs;
    }

    /** 行面改动后重算三笔并同步单面（两处一起动），返回刷新后的单面 */
    private WsidReceiptVo refreshAccountingReceipt(TWsidApplyReceipt receipt, String action, int kind) {
        int[] cnt = recount(receipt.getBatchNo());
        receipt.setEnterCount(cnt[0]);
        receipt.setPendingCount(cnt[1]);
        receipt.setAcceptedCount(cnt[2]);
        receipt.setRejectedCount(cnt[3]);
        receipt.setReceiptKind(kind);
        receipt.setContent(action + "：收下" + cnt[2] + "、退回" + cnt[3] + "、未勾" + cnt[1]);
        this.wsidApplyReceiptMapper.updateById(receipt);
        return toVo(receipt, listRowsOrdered(receipt.getBatchNo()), null);
    }

    /** 重算：[进门落定, 未勾, 收下, 退回]，全部取自行表这一趟，不另跑核对 */
    private int[] recount(String batchNo) {
        int pending = 0;
        int accepted = 0;
        int rejected = 0;
        for (TWsidApplyRow r : listRowsOrdered(batchNo)) {
            int st = r.getStatus() == null ? STATUS_PENDING : r.getStatus();
            if (st == STATUS_ACCEPTED) {
                accepted++;
            } else if (st == STATUS_REJECTED) {
                rejected++;
            } else {
                pending++;
            }
        }
        return new int[] {accepted + rejected, pending, accepted, rejected};
    }

    private List<TWsidApplyRow> listRowsOrdered(String batchNo) {
        List<TWsidApplyRow> rows = this.wsidApplyRowMapper.selectList(new QueryWrapper<TWsidApplyRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
        return rows == null ? Collections.<TWsidApplyRow>emptyList() : rows;
    }

    private Map<Integer, TWsidApplyRow> indexRows(String batchNo) {
        Map<Integer, TWsidApplyRow> index = new LinkedHashMap<Integer, TWsidApplyRow>();
        for (TWsidApplyRow r : listRowsOrdered(batchNo)) {
            index.put(r.getRowNo(), r);
        }
        return index;
    }

    /** 本串记账的那一份单面（已收讫／已回撤）；只对照的那份不算账 */
    private TWsidApplyReceipt accountingReceipt(String batchNo) {
        List<TWsidApplyReceipt> all = this.wsidApplyReceiptMapper.selectList(new QueryWrapper<TWsidApplyReceipt>()
                .eq("batch_no", batchNo)
                .in("receipt_kind", RECEIPT_RECEIVED, RECEIPT_ROLLED_BACK)
                .orderByDesc("create_time"));
        return (all == null || all.isEmpty()) ? null : all.get(0);
    }

    private TWsidApplyReceipt firstReceipt(String batchNo) {
        List<TWsidApplyReceipt> all = this.wsidApplyReceiptMapper.selectList(new QueryWrapper<TWsidApplyReceipt>()
                .eq("batch_no", batchNo)
                .orderByAsc("create_time"));
        return (all == null || all.isEmpty()) ? null : all.get(0);
    }

    private TWsidApplyReceipt newReceipt(String batchNo, String putKind, Integer declaredLimit, int effectiveLimit) {
        TWsidApplyReceipt r = new TWsidApplyReceipt();
        r.setBatchNo(batchNo);
        r.setPutKind(putKind);
        r.setDeclaredLimit(declaredLimit);
        r.setEffectiveLimit(effectiveLimit);
        r.setDelFlag(0);
        return r;
    }

    /** 上限只写在册头那一处：空／负数／没边大数与空着等价，按 150 认 */
    private int normalizeLimit(Integer declaredLimit) {
        if (declaredLimit == null || declaredLimit <= 0 || declaredLimit > LIMIT_SANITY) {
            return DEFAULT_LIMIT;
        }
        return declaredLimit;
    }

    private WsidReceiptVo toVo(TWsidApplyReceipt r, List<TWsidApplyRow> rows, List<Integer> compareDiffs) {
        WsidReceiptVo vo = new WsidReceiptVo();
        vo.setBatchNo(r.getBatchNo());
        vo.setPutKind(r.getPutKind());
        vo.setDeclaredLimit(r.getDeclaredLimit());
        vo.setEffectiveLimit(nz(r.getEffectiveLimit()));
        vo.setReportedCount(nz(r.getReportedCount()));
        vo.setEnterCount(nz(r.getEnterCount()));
        vo.setPendingCount(nz(r.getPendingCount()));
        vo.setAcceptedCount(nz(r.getAcceptedCount()));
        vo.setRejectedCount(nz(r.getRejectedCount()));
        vo.setOverflowCount(nz(r.getOverflowCount()));
        vo.setKind(nz(r.getReceiptKind()));
        vo.setContent(r.getContent());
        vo.setRows(rows);
        vo.setCompareDiffRowNos(compareDiffs);
        return vo;
    }

    private void checkBatchNo(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new IllegalArgumentException("进门只认整串，须给本串批次码");
        }
    }

    /** 来路只两条：站所自录／县局代收；把一行塞进别串不在这里开口子 */
    private void checkPutKind(String putKind) {
        if (!WsidReceiptVo.PUT_STATION.equals(putKind) && !WsidReceiptVo.PUT_COUNTY.equals(putKind)) {
            throw new IllegalArgumentException("来路只认「站所自录」「县局代收」两条：" + putKind);
        }
    }

    private boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    /** 张数按数值对，不认标度（1 与 1.0 是同一笔） */
    private boolean numEq(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /** 行次列表落进 varchar(255) 的记要前收口：多了只留前几行并报总数，不把格子撑破 */
    private String abbrev(List<Integer> rowNos) {
        if (rowNos.size() <= 10) {
            return rowNos.toString();
        }
        return rowNos.subList(0, 10).toString() + "…共" + rowNos.size() + "行";
    }
}
