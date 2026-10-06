package com.fc.v2.service;

import java.util.List;
import java.util.Map;

import com.fc.v2.model.auto.TWsidApplyReceipt;
import com.fc.v2.model.auto.TWsidApplyRow;
import com.fc.v2.model.custom.WsidApplyLineDto;
import com.fc.v2.model.custom.WsidReceiptVo;
import com.fc.v2.model.custom.WsidReconcileVo;
import com.fc.v2.model.custom.WsidRollbackVo;

/**
 * 标识申领名册核收行 Service接口。
 *
 * <p>整串进门形状：一次进门就是一整串（一个 batchNo），系统连着办四步——
 * 点行数 → 取册头上限 → 逐行解析校验 → 每行落一个去处并写出本串收讫单面。
 * 去处只三样：0未勾／1收下／2退回。</p>
 *
 * <p>同一串走两回（站所自录／县局代收）各留一份单面；第二遍只对照不改账。
 * 退回行再报沿用当初排定的行次。回撤只摆得回未勾的行，已落定的一行不回。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface ITWsidApplyRowService {

    /** 按主键回查单行（往年存量行一条不减，照旧数得着） */
    TWsidApplyRow selectTWsidApplyRowById(Long id);

    /** 按串回查全部行（行次升序，含未勾／收下／退回三样） */
    List<TWsidApplyRow> listRows(String batchNo);

    /** 回查某一来路的那一份收讫单面 */
    TWsidApplyReceipt getReceipt(String batchNo, String putKind);

    /**
     * 整串进门（四步连着办，中途不等人手）。
     *
     * <ul>
     *   <li>册头上限只认 declaredLimit 一处：空着／负数／没边的大数，一律按 150 行认；</li>
     *   <li>点行数撞上限就收手，余下的另起一串去接（overflowCount 告知），本串不自行添行；</li>
     *   <li>空串是正经输入：单面只落「空册」，不出收讫结论；</li>
     *   <li>同一串同来路第二遍到：只对照不改账，不回未勾、不另立行；</li>
     *   <li>两个数（qty／chkNum）缺一个，该行退回，不代填、不按零认；两数摆不平退回，缘由逐行写明。</li>
     * </ul>
     */
    WsidReceiptVo receive(String batchNo, String putKind, Integer declaredLimit, List<WsidApplyLineDto> lines);

    /**
     * 中途停过一回后续办：只补没定下来的未勾行，已经定下的那一段不受惊动；
     * fills 以「当初排定的行次 → 补报那一栏」对准未勾行，行次不重新起步；
     * 单面三笔按行面重算，行面单面同一事务。
     */
    WsidReceiptVo resume(String batchNo, Map<Integer, WsidApplyLineDto> fills);

    /**
     * 吃了退回的那一行过几天再报：认当初排定的那个行次，不重新起步、不飘到别串；
     * 已收下的行不收第二回；行面单面同一事务。
     */
    WsidReceiptVo recast(String batchNo, Integer rowNo, WsidApplyLineDto line);

    /**
     * 往回撤：只有还没定下来（未勾）的行摆得回未勾，已经落定（收下／退回）的一截一行也不跟着回；
     * 行面与单面两处同一事务一起动，只动一处即残留（本方法不允许残留）。
     *
     * @return 回撤结果；本串已无未勾行时无物可撤，返回 null（行面单面都不动）
     */
    WsidRollbackVo rollback(String batchNo);

    /**
     * 随机抽一行倒着核：行上去处与单面三笔互相圆得上。三笔全部取自行表重算，
     * 不另跑一趟、不看页面抢先亮出的数；对不齐的行按行次点名，不许说「大致相符」。
     */
    WsidReconcileVo reconcile(String batchNo);
}
