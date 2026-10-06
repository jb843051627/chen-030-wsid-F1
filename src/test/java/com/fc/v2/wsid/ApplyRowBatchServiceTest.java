package com.fc.v2.wsid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TWsidApplyReceiptMapper;
import com.fc.v2.mapper.auto.TWsidApplyRowMapper;
import com.fc.v2.model.auto.TWsidApplyReceipt;
import com.fc.v2.model.auto.TWsidApplyRow;
import com.fc.v2.model.custom.WsidApplyLineDto;
import com.fc.v2.model.custom.WsidReceiptVo;
import com.fc.v2.model.custom.WsidReconcileVo;
import com.fc.v2.model.custom.WsidRollbackVo;
import com.fc.v2.service.impl.TWsidApplyRowServiceImpl;

/**
 * 《标识申领名册》整串进门六段规矩的逐行验收（不启 Spring／不连库，mapper 接内存账）。
 */
public class ApplyRowBatchServiceTest {

    private static final String STATION = WsidReceiptVo.PUT_STATION;
    private static final String COUNTY = WsidReceiptVo.PUT_COUNTY;

    private TWsidApplyRowServiceImpl service;
    private List<TWsidApplyRow> rowStore;
    private List<TWsidApplyReceipt> receiptStore;

    // ------------------------------------------------------------------
    // 内存假库：解释 QueryWrapper 里 eq/in/order 的那几式
    // ------------------------------------------------------------------

    @BeforeEach
    public void setUp() {
        rowStore = new ArrayList<TWsidApplyRow>();
        receiptStore = new ArrayList<TWsidApplyReceipt>();
        service = new TWsidApplyRowServiceImpl();

        TWsidApplyRowMapper rowMapper = mock(TWsidApplyRowMapper.class, new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock inv) {
                String name = inv.getMethod().getName();
                switch (name) {
                    case "insert":
                        rowStore.add(inv.getArgument(0));
                        return 1;
                    case "updateById":
                        return 1;
                    case "selectList":
                        return selectRows(inv.<Wrapper<TWsidApplyRow>>getArgument(0));
                    case "selectOne":
                        List<TWsidApplyRow> one = selectRows(inv.<Wrapper<TWsidApplyRow>>getArgument(0));
                        return one.isEmpty() ? null : one.get(0);
                    default:
                        return defaultValue(inv.getMethod());
                }
            }
        });
        TWsidApplyReceiptMapper receiptMapper = mock(TWsidApplyReceiptMapper.class, new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock inv) {
                String name = inv.getMethod().getName();
                switch (name) {
                    case "insert":
                        receiptStore.add(inv.getArgument(0));
                        return 1;
                    case "updateById":
                        return 1;
                    case "selectList":
                        return selectReceipts(inv.<Wrapper<TWsidApplyReceipt>>getArgument(0));
                    case "selectOne":
                        List<TWsidApplyReceipt> one = selectReceipts(inv.getArgument(0));
                        return one.isEmpty() ? null : one.get(0);
                    default:
                        return defaultValue(inv.getMethod());
                }
            }
        });
        ReflectionTestUtils.setField(service, "wsidApplyRowMapper", rowMapper);
        ReflectionTestUtils.setField(service, "wsidApplyReceiptMapper", receiptMapper);
    }

    private List<TWsidApplyRow> selectRows(Wrapper<TWsidApplyRow> w) {
        List<TWsidApplyRow> hit = new ArrayList<TWsidApplyRow>();
        for (TWsidApplyRow r : rowStore) {
            if (matches(r, w)) {
                hit.add(r);
            }
        }
        if (w != null && w.getTargetSql().contains("ORDER BY") && w.getTargetSql().contains("row_no")) {
            hit.sort((a, b) -> nz(a.getRowNo()) - nz(b.getRowNo()));
        }
        return hit;
    }

    private List<TWsidApplyReceipt> selectReceipts(Wrapper<TWsidApplyReceipt> w) {
        List<TWsidApplyReceipt> hit = new ArrayList<TWsidApplyReceipt>();
        for (TWsidApplyReceipt r : receiptStore) {
            if (matches(r, w)) {
                hit.add(r);
            }
        }
        // 单测里自动填充不生效，create_time 以插入次序顶替
        if (w != null && w.getTargetSql().contains("ORDER BY") && w.getTargetSql().contains("create_time")) {
            if (w.getTargetSql().contains("DESC")) {
                Collections.reverse(hit);
            }
        }
        return hit;
    }

    /** 只解释本服务用到的 eq／in 合取式（无 OR／无嵌套 AND）；ORDER BY 段丢掉（排序另处处理）。 */
    private boolean matches(Object entity, Wrapper<?> w) {
        if (w == null) {
            return true;
        }
        String sql = w.getSqlSegment();
        if (sql == null || sql.isEmpty()) {
            return true;
        }
        String where = sql.contains("ORDER BY") ? sql.substring(0, sql.indexOf("ORDER BY")).trim() : sql;
        @SuppressWarnings("rawtypes")
        Map<String, Object> params = ((AbstractWrapper) w).getParamNameValuePairs();
        // 形如 (col = #{..}) AND (col IN (#{..},#{..})) —— 沿顶层 AND 拆，躲开 IN 里的括号
        for (String clause : splitTopLevel(where)) {
            if (clause.contains(" IN ")) {
                String col = clause.substring(0, clause.indexOf(" IN ")).trim();
                if (!placeholders(clause, params).contains(column(entity, col))) {
                    return false;
                }
            } else if (clause.contains("=")) {
                String col = clause.substring(0, clause.indexOf('=')).trim();
                if (!Objects.equals(column(entity, col), placeholders(clause, params).get(0))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 剥掉最外层那对括号后按 AND 拆（IN 的内层括号里不会出现 AND，够用）。 */
    private List<String> splitTopLevel(String where) {
        String s = where.trim();
        if (s.startsWith("(")) {
            int depth = 0;
            int match = -1;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        match = i;
                        break;
                    }
                }
            }
            if (match == s.length() - 1) {
                s = s.substring(1, s.length() - 1).trim();
            }
        }
        List<String> out = new ArrayList<String>();
        for (String piece : s.split("(?i)\\s+AND\\s+")) {
            if (!piece.trim().isEmpty()) {
                out.add(piece.trim());
            }
        }
        return out;
    }

    private List<Object> placeholders(String fragment, Map<String, Object> params) {
        List<Object> out = new ArrayList<Object>();
        Matcher pm = Pattern.compile("#\\{ew\\.paramNameValuePairs\\.(\\w+)}").matcher(fragment);
        while (pm.find()) {
            Object v = params.get(pm.group(1));
            if (v instanceof Collection) {
                out.addAll((Collection<?>) v);
            } else {
                out.add(v);
            }
        }
        return out;
    }

    private Object column(Object entity, String col) {
        StringBuilder camel = new StringBuilder();
        for (String seg : col.split("_")) {
            camel.append(Character.toUpperCase(seg.charAt(0))).append(seg.substring(1));
        }
        try {
            Method getter = entity.getClass().getMethod("get" + camel.toString());
            return getter.invoke(entity);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Object defaultValue(Method method) {
        Class<?> t = method.getReturnType();
        if (!t.isPrimitive()) {
            return null;
        }
        if (t == boolean.class) {
            return false;
        }
        if (t == long.class) {
            return 0L;
        }
        return 0;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static WsidApplyLineDto line(String item, String qty, String chk) {
        WsidApplyLineDto d = new WsidApplyLineDto();
        d.setItemCode(item);
        d.setQty(qty == null ? null : new BigDecimal(qty));
        d.setChkNum(chk == null ? null : new BigDecimal(chk));
        return d;
    }

    private TWsidApplyRow storedRow(String batch, int rowNo) {
        for (TWsidApplyRow r : rowStore) {
            if (batch.equals(r.getBatchNo()) && r.getRowNo() == rowNo) {
                return r;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 一、四步连着办：点行 → 上限 → 逐行校验 → 落去处＋单面
    // 二、三笔取自逐行那一趟，缺数行不办
    // ------------------------------------------------------------------

    @Test
    public void receive_fourSteps_threeOutcomesAndCounts() {
        List<WsidApplyLineDto> lines = Arrays.asList(
                line("H001", "10", "10"),    // 收下
                line("H002", "20", "18"),    // 退回：差 2 张
                line("H003", null, "5"),     // 少数：这一行不办，落未勾
                line("H004", "5", null));    // 少数：落未勾

        WsidReceiptVo vo = service.receive("B1", STATION, null, lines);

        assertEquals(WsidReceiptVo.KIND_RECEIVED, vo.getKind());
        assertEquals(150, vo.getEffectiveLimit(), "册头空着按 150 行认");
        assertEquals(4, vo.getReportedCount(), "点行数原样刻档");
        assertEquals(1, vo.getAcceptedCount());
        assertEquals(1, vo.getRejectedCount());
        assertEquals(2, vo.getPendingCount());
        assertEquals(2, vo.getEnterCount(), "进门落定笔数 = 收下 + 退回");
        assertEquals(0, vo.getOverflowCount());

        assertEquals(1, storedRow("B1", 1).getStatus().intValue());
        TWsidApplyRow r2 = storedRow("B1", 2);
        assertEquals(2, r2.getStatus().intValue());
        assertEquals("差 2 张", r2.getRejectReason(), "第 7 行式的逐行退回缘由");
        assertEquals(0, storedRow("B1", 3).getStatus().intValue(), "少数不办，不代填不按零认");
        assertEquals(0, storedRow("B1", 4).getStatus().intValue());

        assertNotNull(service.getReceipt("B1", STATION), "单面已写出");
        assertEquals(4, service.listRows("B1").size());
    }

    @Test
    public void receive_fourthStatusCannotBeStored() {
        WsidReceiptVo vo = service.receive("B1z", STATION, null,
                Collections.singletonList(line("H1", "3", "4")));
        for (TWsidApplyRow r : vo.getRows()) {
            assertTrue(Arrays.asList(0, 1, 2).contains(r.getStatus()), "去处只三样");
        }
        assertEquals("多 1 张", vo.getRows().get(0).getRejectReason());
    }

    // ------------------------------------------------------------------
    // 二、上限只册头一处；空/负数/没边大数按 150；撞上限收手，余下另起一串
    // ------------------------------------------------------------------

    @Test
    public void limit_normalizeAndStopAtCap_overflowGoesElsewhere() {
        WsidReceiptVo neg = service.receive("B2a", STATION, -5, oneOneLines(151));
        assertEquals(150, neg.getEffectiveLimit());
        assertEquals(151, neg.getReportedCount());
        assertEquals(150, service.listRows("B2a").size(), "本串只接 150，不许自行添");
        assertEquals(1, neg.getOverflowCount(), "余下 1 行另起一串");
        assertEquals(150, neg.getAcceptedCount());

        WsidReceiptVo huge = service.receive("B2b", STATION, 999999, oneOneLines(160));
        assertEquals(150, huge.getEffectiveLimit(), "没边的大数与空着等价");
        assertEquals(10, huge.getOverflowCount());

        WsidReceiptVo small = service.receive("B2c", STATION, 2, oneOneLines(3));
        assertEquals(2, small.getEffectiveLimit(), "册头写 2 就是 2，别处没有第二道");
        assertEquals(2, service.listRows("B2c").size());
        assertEquals(1, small.getOverflowCount());
        assertEquals(3, small.getReportedCount(), "点行点的是全数，不是截断后的数");
    }

    private List<WsidApplyLineDto> oneOneLines(int n) {
        List<WsidApplyLineDto> l = new ArrayList<WsidApplyLineDto>();
        for (int i = 0; i < n; i++) {
            l.add(line("H" + i, "1", "1"));
        }
        return l;
    }

    @Test
    public void emptyBooklet_isValidInput_emptyMarkOnly() {
        WsidReceiptVo vo = service.receive("B3", STATION, null, new ArrayList<WsidApplyLineDto>());
        assertEquals(WsidReceiptVo.KIND_EMPTY, vo.getKind());
        assertEquals("空册", vo.getContent());
        assertEquals(0, vo.getEnterCount());
        assertEquals(0, vo.getAcceptedCount());
        assertEquals(0, vo.getRejectedCount());
        assertTrue(service.listRows("B3").isEmpty(), "不从别串拉一行填数");
        assertNotNull(service.getReceipt("B3", STATION));
    }

    // ------------------------------------------------------------------
    // 四、一家主体两行各定各；行次系统排
    // ------------------------------------------------------------------

    @Test
    public void sameHolderTwoRows_bothKeptAndJudgedSeparately() {
        WsidReceiptVo vo = service.receive("B4", STATION, null, Arrays.asList(
                line("H9", "3", "3"),
                line("H9", "5", "4")));
        assertEquals(2, vo.getRows().size(), "不并作一行、谁也吞不掉谁");
        assertEquals(1, storedRow("B4", 1).getStatus().intValue());
        assertEquals(2, storedRow("B4", 2).getStatus().intValue());
        assertEquals("差 1 张", storedRow("B4", 2).getRejectReason());
    }

    // ------------------------------------------------------------------
    // 五、同一串走两回：两份单面；第二遍只对照不改账
    // ------------------------------------------------------------------

    @Test
    public void secondPass_compareOnly_twoReceiptsAccountsUntouched() {
        List<WsidApplyLineDto> lines = Arrays.asList(line("H1", "1", "1"), line("H2", "20", "18"));
        service.receive("B5", STATION, null, lines);

        WsidReceiptVo second = service.receive("B5", COUNTY, null, lines);
        assertEquals(WsidReceiptVo.KIND_COMPARED, second.getKind());
        assertNotNull(service.getReceipt("B5", STATION));
        assertNotNull(service.getReceipt("B5", COUNTY), "两份单面都留");
        assertEquals(2, second.getReportedCount(), "对照遍照头一份刻档回话");
        assertEquals(150, second.getEffectiveLimit());
        assertEquals(2, rowStore.size(), "本串不许再摆一遍另立一处");
        assertEquals(1, storedRow("B5", 1).getStatus().intValue(), "收下的不回未勾");
        assertEquals(2, storedRow("B5", 2).getStatus().intValue());
        assertTrue(second.getCompareDiffRowNos().isEmpty());

        // 第二遍把张数串到别行：只点名，账不动
        WsidReceiptVo tampered = service.receive("B5", COUNTY, null,
                Arrays.asList(line("H1", "9", "9"), line("H2", "20", "18")));
        assertEquals(Collections.singletonList(1), tampered.getCompareDiffRowNos());
        assertEquals(new BigDecimal("1"), storedRow("B5", 1).getQty(), "头一遍记号不翻回去");

        // 同来路补报一遍：仍只对照，不第三份单面
        int receiptsBefore = receiptStore.size();
        WsidReceiptVo repeat = service.receive("B5", STATION, null, lines);
        assertEquals(WsidReceiptVo.KIND_COMPARED, repeat.getKind());
        assertEquals(receiptsBefore, receiptStore.size(), "同串同来路不另立单面");
        assertEquals(2, rowStore.size());
    }

    // ------------------------------------------------------------------
    // 四／五、续办只补未勾；退回再报行次不变
    // ------------------------------------------------------------------

    @Test
    public void resume_onlyFillsPending_settledUntouched() {
        service.receive("B6", STATION, null, Arrays.asList(
                line("H1", "1", "1"),
                line("H2", null, "2"),
                line("H3", "8", null)));

        Map<Integer, WsidApplyLineDto> fills = new LinkedHashMap<Integer, WsidApplyLineDto>();
        fills.put(2, line("H2", "2", "2"));
        fills.put(3, line("H3", "8", "8"));
        WsidReceiptVo vo = service.resume("B6", fills);

        assertEquals(3, vo.getAcceptedCount());
        assertEquals(0, vo.getRejectedCount());
        assertEquals(0, vo.getPendingCount());
        assertEquals(3, vo.getEnterCount());
        assertEquals(new BigDecimal("1"), storedRow("B6", 1).getQty(), "定下的那一段不受惊动");
        assertEquals(2, storedRow("B6", 2).getRowNo().intValue(), "行次认当初排定的");
        assertEquals(3, storedRow("B6", 3).getRowNo().intValue());

        assertThrows(IllegalStateException.class,
                () -> service.resume("B6", Collections.singletonMap(1, line("H1", "2", "2"))),
                "已定行续办不许惊动");
        assertThrows(IllegalArgumentException.class,
                () -> service.resume("B6", Collections.singletonMap(9, line("H9", "1", "1"))),
                "不许往别串拉行");
    }

    @Test
    public void recast_rejectedRowKeepsOriginalRowNo() {
        service.receive("B7", STATION, null, Collections.singletonList(line("H1", "20", "18")));
        assertEquals(2, storedRow("B7", 1).getStatus().intValue());

        WsidReceiptVo vo = service.recast("B7", 1, line("H1", "18", "18"));
        assertEquals(1, vo.getAcceptedCount());
        assertEquals(0, vo.getRejectedCount());
        assertEquals(1, rowStore.size(), "不重新起步、不另立一行");
        assertEquals(1, storedRow("B7", 1).getStatus().intValue());
        assertEquals(1, storedRow("B7", 1).getRowNo().intValue());

        assertThrows(IllegalStateException.class,
                () -> service.recast("B7", 1, line("H1", "18", "18")),
                "收下的不收第二回");
        assertThrows(IllegalArgumentException.class,
                () -> service.recast("B7", 5, line("H5", "1", "1")),
                "行不会飘到别串去");
    }

    // ------------------------------------------------------------------
    // 五、回撤：未勾摆回，落定不回，行面单面一起动
    // ------------------------------------------------------------------

    @Test
    public void rollback_onlyPendingReturns_settledStays() {
        service.receive("B8", STATION, null, Arrays.asList(
                line("H1", "1", "1"),    // 收下
                line("H2", "20", "18"),  // 退回
                line("H3", null, "3"))); // 未勾

        WsidRollbackVo vo = service.rollback("B8");
        assertEquals(Collections.singletonList(3), vo.getResetRowNos());
        assertEquals(Arrays.asList(1, 2), vo.getSettledUntouchedRowNos(), "落定那一截一行不回");
        assertEquals(1, storedRow("B8", 1).getStatus().intValue());
        assertEquals(2, storedRow("B8", 2).getStatus().intValue());
        assertEquals(0, storedRow("B8", 3).getStatus().intValue());
        assertEquals(2, vo.getEnterCount());
        assertEquals(1, vo.getAcceptedCount());
        assertEquals(1, vo.getRejectedCount());
        assertEquals(3, service.getReceipt("B8", STATION).getReceiptKind().intValue(), "单面同步成已回撤");

        // 摆回的未勾行续办后照样结得了；退回的第 2 行不受惊动，仍是退回
        WsidReceiptVo resumed = service.resume("B8",
                Collections.singletonMap(3, line("H3", "3", "3")));
        assertEquals(2, resumed.getAcceptedCount());
        assertEquals(1, resumed.getRejectedCount());
        assertEquals(0, resumed.getPendingCount());
        assertEquals(3, resumed.getEnterCount());
        assertNull(service.rollback("B8"), "没有未勾行了，无物可撤");
    }

    // ------------------------------------------------------------------
    // 六、倒核：三笔按行表重算，随机抽一行倒核，按行点名
    // ------------------------------------------------------------------

    @Test
    public void reconcile_countsFromRows_sampledRowSound() {
        service.receive("B9", STATION, null, Arrays.asList(
                line("H1", "10", "10"),
                line("H2", "20", "18"),
                line("H3", null, "1")));

        WsidReconcileVo vo = service.reconcile("B9");
        assertTrue(vo.isConsistent());
        assertEquals(2, vo.getRecountedEnterCount());
        assertEquals(1, vo.getRecountedAcceptedCount());
        assertEquals(1, vo.getRecountedRejectedCount());
        assertEquals(vo.getRecountedEnterCount(), vo.getReceiptEnterCount());
        assertNotNull(vo.getCheckedRowNo());
        assertTrue(vo.isCheckedRowSound());
        assertTrue(vo.getDiffRows().isEmpty());
    }

    @Test
    public void reconcile_driftsNamedByRow_noRoughlyMatch() {
        service.receive("B10", STATION, null, Arrays.asList(
                line("H1", "1", "1"),
                line("H2", "3", "2")));

        // 页面抢先亮错的数写进单面：以行表那一趟为准 → 不符
        TWsidApplyReceipt rc = service.getReceipt("B10", STATION);
        rc.setAcceptedCount(99);
        // 有人塞了第四样去处
        storedRow("B10", 1).setStatus(9);

        WsidReconcileVo vo = service.reconcile("B10");
        assertFalse(vo.isConsistent());
        boolean row1Named = vo.getDiffRows().stream()
                .anyMatch(d -> d.getRowNo() == 1 && d.getMessage().contains("第四样"));
        assertTrue(row1Named, "对不齐的行按行次点名");
        assertEquals(0, vo.getRecountedAcceptedCount(), "第四样是脏行，三笔只认真正的收下/退回，不记进收下");
        assertEquals(1, vo.getRecountedRejectedCount());
    }

    // ------------------------------------------------------------------
    // 进门规矩与存量行
    // ------------------------------------------------------------------

    @Test
    public void receive_rejectsSplitSubmissionAndUnknownPutKind() {
        assertThrows(IllegalArgumentException.class,
                () -> service.receive("B11", "市里补报", null, oneOneLines(1)),
                "来路只认站所自录／县局代收两条");
        assertThrows(IllegalArgumentException.class,
                () -> service.receive("  ", STATION, null, oneOneLines(1)),
                "进门只认整串，须给批次码");
    }

    @Test
    public void historicalRows_oneNotRemoved_stillCountable() {
        TWsidApplyRow legacy = new TWsidApplyRow();
        legacy.setBatchNo("OLD");
        legacy.setRowNo(1);
        legacy.setItemCode("H-OLD");
        legacy.setQty(new BigDecimal("12"));
        legacy.setChkNum(new BigDecimal("12"));
        legacy.setStatus(1);
        legacy.setDelFlag(0);
        rowStore.add(legacy);

        service.receive("B12", STATION, null, Collections.singletonList(line("H1", "1", "1")));

        List<TWsidApplyRow> old = service.listRows("OLD");
        assertEquals(1, old.size(), "往年入账的存量行一条不减");
        assertEquals(new BigDecimal("12"), old.get(0).getQty());
    }
}
