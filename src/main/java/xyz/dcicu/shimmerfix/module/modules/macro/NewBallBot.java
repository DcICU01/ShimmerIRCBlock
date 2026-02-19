package xyz.dcicu.shimmerfix.module.modules.macro;

import dev.greencat.shimmer.event.events.ActionBarRenderEvent;
import dev.greencat.shimmer.event.events.TickEvent;
import dev.greencat.shimmer.eventbus.ShimmerSubscribe;
import dev.greencat.shimmer.module.Module;
import dev.greencat.shimmer.module.modules.render.Hud;
import dev.greencat.shimmer.setting.settings.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.dcicu.shimmerfix.util.ArmorStandUtil;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class NewBallBot extends Module {

    public int getBallCount() {
        return predictors.size(); //公开周围沙滩球的数量
    }

    // ==================== 常量 ====================
    private static final String BEACH_BALL_TEXTURE =
            "ewogICJ0aW1lc3RhbXAiIDogMTczNjQyNzQ4ODAwNCwKICAicHJvZmlsZUlkIiA6ICIzN2JhNjRkYzkxOTg0OGI4YjZhNDdiYTg0ZDgwNDM3MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJTb3lLb3NhIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzJhZGY5ZDcxMzY3Y2Q2ZTUwNWZiNDhjYWFhNWFjZGNkZmYyYTA5ZjY2YzQ4OGRhZjA0ZDA0NWVlMGJmNTI4ZTEiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==";

    private static final int UPDATE_INTERVAL = 1;          // 每 1 tick 更新一次数据（约0.1秒）
    private static final int DISPLAY_INTERVAL = 2;         // 每 2 tick 显示一次落点
    private static final double SCAN_RADIUS = 50.0;
    private static final double MOVE_THRESHOLD = 0.2;      // 目标更新阈值（方块）
    private static final int MAX_HISTORY = 200;
    private static final int MAX_PREDICT_STEPS = 300;
    private static final double HARD_STOP_DISTANCE = 0.2;  // 硬停止距离（精确停止阈值）

    // ==================== 设置项 ====================
    private final BooleanSetting autoWalk = new BooleanSetting("Auto Walk", "自动走向最近球的落点", true);
    private final BooleanSetting reverseForward = new BooleanSetting("Reverse Forward", "反转前后移动方向（如果感觉前后反了可以开启）", true);
    private final BooleanSetting reverseStrafe = new BooleanSetting("Reverse Strafe", "反转左右移动方向（如果感觉左右反了可以开启）", false);
    private final BooleanSetting autoDisable = new BooleanSetting("Auto Disable", "自动禁用模块（≥40次或无球时）", true);

    // ==================== 状态变量 ====================
    private final Set<Integer> beachBallIds = new HashSet<>();
    private final Map<Integer, Predictor> predictors = new HashMap<>();
    private ArmorStand nearestBall = null;

    // 视角锁定与潜行
    private boolean perspectiveLocked = false;
    private boolean sneakForced = false;

    // 无球检测计时
    private long lastBallDetectedTime = System.currentTimeMillis();

    // ==================== 自动禁用 API ====================
    private static final List<Runnable> autoDisableListeners = new CopyOnWriteArrayList<>();

    public static void registerAutoDisableListener(Runnable listener) {
        autoDisableListeners.add(listener);
    }

    public static void unregisterAutoDisableListener(Runnable listener) {
        autoDisableListeners.remove(listener);
    }

    public NewBallBot() {
        super("NewBallBot", "沙滩球自动寻路", -1, Category.MACRO);
        addSettings(autoWalk, reverseForward, reverseStrafe, autoDisable);
        this.needDisable = false;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        beachBallIds.clear();
        predictors.clear();
        nearestBall = null;
        lastBallDetectedTime = System.currentTimeMillis();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // 锁定视角：面向正南（Yaw=0）并俯视（Pitch=90），不再保存原始视角
            mc.player.setYRot(0);
            mc.player.setXRot(90);
            perspectiveLocked = true;

            // 强制全程潜行
            mc.options.keyShift.setDown(true);
            sneakForced = true;
        }

        Hud.onMessage(Component.literal("§a[NewBallBot] 已启用"));
    }

    @Override
    public void onDisable() {
        SimpleWalker.cancel();

        Minecraft mc = Minecraft.getInstance();
        if (perspectiveLocked && mc.player != null) {
            // 仅取消视角锁定（不再恢复原始视角）
            perspectiveLocked = false;
        }
        if (sneakForced && mc.player != null) {
            mc.options.keyShift.setDown(false);
            sneakForced = false;
        }

        beachBallIds.clear();
        predictors.clear();
        nearestBall = null;
        super.onDisable();
    }

    @ShimmerSubscribe
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 每 tick 强制保持视角
        if (perspectiveLocked) {
            mc.player.setYRot(0);
            mc.player.setXRot(90);
            mc.options.keyShift.setDown(true);
        }

        // --- 预测数据更新（每 UPDATE_INTERVAL tick 执行一次）---
        if (mc.player.tickCount % UPDATE_INTERVAL == 0) {
            if (mc.level instanceof ClientLevel level) {
                updatePredictions(level, mc.player.position());
            }
        }

        // --- 无球检测：如果 predictors 不为空，更新检测时间 ---
        if (!predictors.isEmpty()) {
            lastBallDetectedTime = System.currentTimeMillis();
        }

        // --- 自动禁用检查：如果没有检测到任何球且 autoDisable 开启，且超过3秒无球，则触发自动禁用 ---
        if (autoDisable.isEnabled() && predictors.isEmpty() && System.currentTimeMillis() - lastBallDetectedTime > 3000) {
            triggerAutoDisable();
            return; // 直接返回，避免后续移动
        }

        // --- 移动控制 ---
        if (autoWalk.isEnabled() && nearestBall != null) {
            Predictor predictor = predictors.get(nearestBall.getId());
            if (predictor != null) {
                LorenzVec landing = predictor.getPredictedLanding();
                if (landing != null) {
                    // 有预测落点，使用预测值
                    SimpleWalker.walkTo(landing.x, landing.z, reverseForward.isEnabled(), reverseStrafe.isEnabled());
                } else {
                    // 预测尚未完成，暂时使用球的当前位置作为目标
                    SimpleWalker.walkTo(nearestBall.getX(), nearestBall.getZ(), reverseForward.isEnabled(), reverseStrafe.isEnabled());
                }
            } else {
                SimpleWalker.cancel();
            }
        } else {
            // 自动行走关闭或无球时，取消移动
            SimpleWalker.cancel();
        }

        // 驱动简单移动
        SimpleWalker.tick();

        // --- 显示落点信息（每 DISPLAY_INTERVAL tick 执行）---
        if (mc.player.tickCount % DISPLAY_INTERVAL == 0) {
            displayLandingInfo(mc);
        }
    }

    @ShimmerSubscribe
    public void onActionBar(ActionBarRenderEvent event) {
        if (!isEnabled() || !autoDisable.isEnabled() || event.text == null) return;

        String raw = StringUtil.stripColor(event.text.getString());
        // 改为直接匹配 "Bounces: 40"（与 FullyBallBot 一致）
        if (raw.contains("Bounces: 40") ||
                raw.contains("Bounces: 41") ||
                raw.contains("Bounces: 42") ||
                raw.contains("Bounces: 43")) {
            triggerAutoDisable();
        }
    }

    /**
     * 触发自动禁用：调用所有监听器并禁用自身
     */
    private void triggerAutoDisable() {
        // 触发监听器
        for (Runnable listener : autoDisableListeners) {
            listener.run();
        }
        // 禁用模块
        setEnabled(false);
    }

    // 更新预测数据并找出最近球
    private void updatePredictions(ClientLevel level, Vec3 playerPos) {
        AABB scanBox = new AABB(
                playerPos.x - SCAN_RADIUS, playerPos.y - SCAN_RADIUS, playerPos.z - SCAN_RADIUS,
                playerPos.x + SCAN_RADIUS, playerPos.y + SCAN_RADIUS, playerPos.z + SCAN_RADIUS
        );

        List<Entity> entities = level.getEntities((Entity) null, scanBox, e -> e instanceof ArmorStand);
        Map<Integer, ArmorStand> currentBalls = new HashMap<>();

        for (Entity entity : entities) {
            ArmorStand stand = (ArmorStand) entity;
            int id = stand.getId();
            if (beachBallIds.contains(id) || ArmorStandUtil.hasTexture(stand, BEACH_BALL_TEXTURE)) {
                beachBallIds.add(id);
                currentBalls.put(id, stand);
            }
        }

        beachBallIds.removeIf(id -> !currentBalls.containsKey(id));
        predictors.keySet().removeIf(id -> !currentBalls.containsKey(id));

        for (ArmorStand stand : currentBalls.values()) {
            int id = stand.getId();
            Predictor predictor = predictors.get(id);
            if (predictor == null) {
                predictor = new Predictor(new LorenzVec(stand.position()));
                predictors.put(id, predictor);
            } else {
                predictor.newData(new LorenzVec(stand.position()));
            }
        }

        // 找出离玩家最近的沙滩球（缓存）
        nearestBall = null;
        double minDistSq = Double.MAX_VALUE;
        for (ArmorStand stand : currentBalls.values()) {
            double dx = stand.getX() - playerPos.x;
            double dz = stand.getZ() - playerPos.z;
            double distSq = dx*dx + dz*dz;
            if (distSq < minDistSq) {
                minDistSq = distSq;
                nearestBall = stand;
            }
        }
    }

    private void displayLandingInfo(Minecraft mc) {
        if (!predictors.isEmpty() && nearestBall != null) {
            Predictor p = predictors.get(nearestBall.getId());
            if (p != null) {
                LorenzVec landing = p.getPredictedLanding();
                if (landing != null) {
                    String coord = String.format("(%.2f, %.2f)", landing.x, landing.z);
                    Hud.onMessage(Component.literal("§a发现 " + predictors.size() + " 个球，落点：" + coord));
                } else {
                    Hud.onMessage(Component.literal("§7发现 " + predictors.size() + " 个球，预测中..."));
                }
            } else {
                Hud.onMessage(Component.literal("§7发现 " + predictors.size() + " 个球，预测中..."));
            }
        } else if (predictors.isEmpty() && mc.player.tickCount % 40 == 0) {
            Hud.onMessage(Component.literal("§7无沙滩球"));
        }
    }

    // ==================== 内部预测类 ====================
    public static class LorenzVec {
        public double x, y, z;
        public LorenzVec(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        public LorenzVec(Vec3 vec) { this(vec.x, vec.y, vec.z); }
        public double distanceTo(LorenzVec other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return Math.sqrt(dx*dx + dy*dy + dz*dz);
        }
        public LorenzVec subtract(LorenzVec other) { return new LorenzVec(x - other.x, y - other.y, z - other.z); }
        public LorenzVec add(double dx, double dy, double dz) { return new LorenzVec(x + dx, y + dy, z + dz); }
        @Override public String toString() { return String.format("(%.2f, %.2f, %.2f)", x, y, z); }
    }

    private interface Model {
        LorenzVec predictLanding(int startIdx, int currentIdx, double minY, List<LorenzVec> data);
        int minimumToPredict();
    }

    private abstract static class PolyModel implements Model {
        protected abstract int getT1(int start, int current, int dataSize);
        protected abstract int getT2(int start, int current, int dataSize);
        protected abstract int getT3(int start, int current, int dataSize);

        protected double yTransform(int t, List<LorenzVec> data) { return data.get(t).y; }
        protected double dX(int current, List<LorenzVec> data) { return data.get(current).x - data.get(current - 1).x; }
        protected double dZ(int current, List<LorenzVec> data) { return data.get(current).z - data.get(current - 1).z; }

        @Override
        public LorenzVec predictLanding(int startIdx, int currentIdx, double minY, List<LorenzVec> data) {
            int t1 = getT1(startIdx, currentIdx, data.size());
            int t2 = getT2(startIdx, currentIdx, data.size());
            int t3 = getT3(startIdx, currentIdx, data.size());
            if (t1 < 0 || t1 >= data.size() || t2 < 0 || t2 >= data.size() || t3 < 0 || t3 >= data.size())
                return null;

            double y1 = yTransform(t1, data), y2 = yTransform(t2, data), y3 = yTransform(t3, data);
            double a = ((y3 - y1) * (t2 - t1) + (y2 - y1) * (t1 - t3)) /
                    ((t3 * t3 - t1 * t1) * (t2 - t1) + (t2 * t2 - t1 * t1) * (t1 - t3));
            double b = ((y2 - y1) - a * (t2 * t2 - t1 * t1)) / (t2 - t1);
            double c = y1 - b * t1 - a * t1 * t1;

            double dx = dX(currentIdx, data);
            double dz = dZ(currentIdx, data);

            int t = currentIdx + 1;
            LorenzVec prev = data.get(currentIdx);
            for (int step = 0; step < MAX_PREDICT_STEPS; step++) {
                double y = a * t * t + b * t + c;
                if (y < minY) {
                    return prev;
                }
                prev = new LorenzVec(prev.x + dx, y, prev.z + dz);
                t++;
            }
            return null;
        }
    }

    private static class SmallPoly extends PolyModel {
        @Override public int minimumToPredict() { return 3; }
        @Override protected int getT1(int start, int current, int size) { return current; }
        @Override protected int getT2(int start, int current, int size) { return current - 1; }
        @Override protected int getT3(int start, int current, int size) { return current - 2; }
    }

    private static class AveragePoly extends PolyModel {
        @Override public int minimumToPredict() { return 7; }
        @Override protected int getT1(int start, int current, int size) { return current - 1; }
        @Override protected int getT2(int start, int current, int size) { return current - 3; }
        @Override protected int getT3(int start, int current, int size) { return current - 5; }
        @Override protected double yTransform(int t, List<LorenzVec> data) {
            double sum = 0; int cnt = 0;
            for (int i = t - 1; i <= t + 1; i++) {
                if (i >= 0 && i < data.size()) { sum += data.get(i).y; cnt++; }
            }
            return cnt > 0 ? sum / cnt : data.get(t).y;
        }
        @Override protected double dX(int current, List<LorenzVec> data) {
            double sum = 0;
            for (int i = current; i >= current - 2; i--) {
                if (i - 1 >= 0) sum += data.get(i).x - data.get(i - 1).x;
            }
            return sum / 3;
        }
        @Override protected double dZ(int current, List<LorenzVec> data) {
            double sum = 0;
            for (int i = current; i >= current - 2; i--) {
                if (i - 1 >= 0) sum += data.get(i).z - data.get(i - 1).z;
            }
            return sum / 3;
        }
    }

    private static class SpreadPoly extends PolyModel {
        @Override public int minimumToPredict() { return 5; }
        @Override protected int getT1(int start, int current, int size) { return current - 1; }
        @Override protected int getT2(int start, int current, int size) { return (current - start) / 2 + start; }
        @Override protected int getT3(int start, int current, int size) { return start + 1; }
        @Override protected double yTransform(int t, List<LorenzVec> data) {
            double sum = 0; int cnt = 0;
            for (int i = t - 1; i <= t + 1; i++) {
                if (i >= 0 && i < data.size()) { sum += data.get(i).y; cnt++; }
            }
            return cnt > 0 ? sum / cnt : data.get(t).y;
        }
    }

    private static class Predictor {
        private final Deque<LorenzVec> data = new ArrayDeque<>(MAX_HISTORY + 1);
        private int startIndex = 0;
        private double minY = Double.MAX_VALUE;
        private int updated = 0;
        private LorenzVec lastPosition;
        private boolean positive = true;
        private long lastChange = System.currentTimeMillis();
        private int bounceCounter = 0;
        private int lastBounceCounter = 0;
        private LorenzVec predictedLanding = null;

        private List<LorenzVec> asList() {
            return new ArrayList<>(data);
        }

        public Predictor(LorenzVec start) {
            data.add(start);
            newData(start);
        }

        public void newData(LorenzVec newPos) {
            updateDirection(newPos);

            if (bounceCounter > lastBounceCounter) {
                data.clear();
                data.add(newPos);
                startIndex = 0;
                minY = newPos.y;
                lastBounceCounter = bounceCounter;
                return;
            }

            data.addLast(newPos);
            if (data.size() > MAX_HISTORY) {
                data.removeFirst();
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                LorenzVec playerPos = new LorenzVec(mc.player.position());
                if (newPos.distanceTo(playerPos) < 2.1) {
                    startIndex = data.size() - 1;
                    minY = newPos.y;
                }
            }
            updated++;
            if (updated >= 3) {
                predictedLanding = computeLanding();
                updated = 0;
            }
        }

        private void updateDirection(LorenzVec newPosition) {
            if (lastPosition != null) {
                if (lastPosition.distanceTo(newPosition) < 0.3) return;
                if (System.currentTimeMillis() - lastChange < 800) return;
                double diff = newPosition.y - lastPosition.y;
                boolean isPositive = diff > 0;
                if (isPositive && !positive) {
                    bounceCounter++;
                    lastChange = System.currentTimeMillis();
                }
                positive = isPositive;
            }
            lastPosition = newPosition;
        }

        private LorenzVec computeLanding() {
            if (data.size() < 3) return null;
            int current = data.size() - 1;
            int presentValues = current - startIndex;

            List<LorenzVec> list = asList();

            List<LandingWeight> candidates = new ArrayList<>(3);
            addCandidate(candidates, new SmallPoly(), 1, list, startIndex, current, minY, presentValues);
            addCandidate(candidates, new AveragePoly(), 2, list, startIndex, current, minY, presentValues);
            addCandidate(candidates, new SpreadPoly(), 1, list, startIndex, current, minY, presentValues);

            if (candidates.isEmpty()) return null;

            double totalWeight = 0, sumX = 0, sumZ = 0;
            for (LandingWeight lw : candidates) {
                totalWeight += lw.weight;
                sumX += lw.landing.x * lw.weight;
                sumZ += lw.landing.z * lw.weight;
            }
            double avgX = sumX / totalWeight;
            double avgZ = sumZ / totalWeight;

            LandingWeight best = candidates.get(0);
            double bestDist = Double.MAX_VALUE;
            for (LandingWeight lw : candidates) {
                double dx = lw.landing.x - avgX;
                double dz = lw.landing.z - avgZ;
                double distSq = dx*dx + dz*dz;
                if (distSq < bestDist) {
                    bestDist = distSq;
                    best = lw;
                }
            }
            return best.landing;
        }

        private void addCandidate(List<LandingWeight> list, Model model, int weight,
                                  List<LorenzVec> data, int startIdx, int currentIdx, double minY, int presentValues) {
            if (model.minimumToPredict() <= presentValues) {
                LorenzVec landing = model.predictLanding(startIdx, currentIdx, minY, data);
                if (landing != null) {
                    list.add(new LandingWeight(landing, weight));
                }
            }
        }

        public LorenzVec getPredictedLanding() {
            return predictedLanding;
        }

        private static class LandingWeight {
            LorenzVec landing;
            int weight;
            LandingWeight(LorenzVec landing, int weight) { this.landing = landing; this.weight = weight; }
        }
    }

    // ==================== 简单移动类（使用精确坐标）====================
    private static class SimpleWalker {
        private static boolean isWalking = false;
        private static double targetX, targetZ;
        private static boolean reverseForward = false;
        private static boolean reverseStrafe = false;
        private static final double DIRECTION_THRESHOLD = 0.1;

        public static void walkTo(double x, double z, boolean revForward, boolean revStrafe) {
            targetX = x;
            targetZ = z;
            reverseForward = revForward;
            reverseStrafe = revStrafe;
            isWalking = true;
        }

        public static void cancel() {
            isWalking = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.options.keyUp.setDown(false);
                mc.options.keyDown.setDown(false);
                mc.options.keyLeft.setDown(false);
                mc.options.keyRight.setDown(false);
            }
        }

        public static void tick() {
            if (!isWalking) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            double dx = targetX - mc.player.getX();
            double dz = targetZ - mc.player.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance < HARD_STOP_DISTANCE) {
                cancel();
                return;
            }

            // 前后移动（基于 Yaw=0，面向南）
            boolean forwardPressed = dz < -DIRECTION_THRESHOLD;      // 目标在北（负Z）应向前
            boolean backwardPressed = dz > DIRECTION_THRESHOLD;      // 目标在南（正Z）应向后

            if (reverseForward) {
                boolean temp = forwardPressed;
                forwardPressed = backwardPressed;
                backwardPressed = temp;
            }

            if (forwardPressed) {
                mc.options.keyUp.setDown(true);
                mc.options.keyDown.setDown(false);
            } else if (backwardPressed) {
                mc.options.keyDown.setDown(true);
                mc.options.keyUp.setDown(false);
            } else {
                mc.options.keyUp.setDown(false);
                mc.options.keyDown.setDown(false);
            }

            // 左右移动（基于 Yaw=0）
            boolean leftPressed = dx > DIRECTION_THRESHOLD;           // 目标在东（正X）应向左（A）
            boolean rightPressed = dx < -DIRECTION_THRESHOLD;         // 目标在西（负X）应向右（D）

            if (reverseStrafe) {
                boolean temp = leftPressed;
                leftPressed = rightPressed;
                rightPressed = temp;
            }

            if (leftPressed) {
                mc.options.keyLeft.setDown(true);
                mc.options.keyRight.setDown(false);
            } else if (rightPressed) {
                mc.options.keyRight.setDown(true);
                mc.options.keyLeft.setDown(false);
            } else {
                mc.options.keyLeft.setDown(false);
                mc.options.keyRight.setDown(false);
            }
        }
    }
}