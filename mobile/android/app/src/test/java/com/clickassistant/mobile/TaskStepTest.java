package com.clickassistant.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * TaskStep 模型校验单元测试。
 * 覆盖字段校验、序列化/反序列化，以及移除 WAIT 类型后的兼容性。
 */
public final class TaskStepTest {

    @Test
    public void validateForSave_rejectsEmptyName() {
        TaskStep step = new TaskStep();
        step.setName("");

        try {
            step.validateForSave();
            fail("应抛出异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("名称"));
        }
    }

    @Test
    public void validateForSave_acceptsValidTap() {
        TaskStep step = new TaskStep();
        step.setName("点击测试");
        step.setActionType(TaskActionType.TAP);
        step.setX(100);
        step.setY(200);
        step.setTapCount(1);

        step.validateForSave(); // 不抛异常即通过
    }

    @Test
    public void validateForSave_rejectsNegativeDelay() {
        TaskStep step = new TaskStep();
        step.setName("延迟测试");
        step.setBeforeDelayMs(-1);

        try {
            step.validateForSave();
            fail("应抛出异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("等待时间"));
        }
    }

    @Test
    public void validateForSave_rejectsZeroTapCount() {
        TaskStep step = new TaskStep();
        step.setName("零点击");
        step.setActionType(TaskActionType.TAP);
        step.setX(0);
        step.setY(0);
        step.setTapCount(0);

        try {
            step.validateForSave();
            fail("应抛出异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("点击次数"));
        }
    }

    @Test
    public void validateForSave_rejectsNegativeCoord() {
        TaskStep step = new TaskStep();
        step.setName("负坐标");
        step.setActionType(TaskActionType.TAP);
        step.setX(-1);
        step.setY(100);
        step.setTapCount(1);

        try {
            step.validateForSave();
            fail("应抛出异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("坐标"));
        }
    }

    @Test
    public void validateForSave_rejectsZeroSwipeDuration() {
        TaskStep step = new TaskStep();
        step.setName("零滑动时长");
        step.setActionType(TaskActionType.SWIPE);
        step.setX(100);
        step.setY(200);
        step.setEndX(300);
        step.setEndY(400);
        step.setDurationMs(0);

        try {
            step.validateForSave();
            fail("应抛出异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("持续时间"));
        }
    }

    @Test
    public void validateForSave_rejectsEmptyTextInput() {
        TaskStep step = new TaskStep();
        step.setName("空文本");
        step.setActionType(TaskActionType.TEXT_INPUT);
        step.setTextContent("");

        try {
            step.validateForSave();
            fail("应抛出异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("文本"));
        }
    }

    @Test
    public void validateForSave_acceptsTextInput() {
        TaskStep step = new TaskStep();
        step.setName("文本测试");
        step.setActionType(TaskActionType.TEXT_INPUT);
        step.setTextContent("Hello");

        step.validateForSave(); // 不抛异常即通过
    }

    @Test
    public void getSummary_tap_returnsCoordinatesAndCount() {
        TaskStep step = new TaskStep();
        step.setActionType(TaskActionType.TAP);
        step.setX(100);
        step.setY(200);
        step.setTapCount(3);

        String summary = step.getSummary();
        assertTrue(summary.contains("(100,200)"));
        assertTrue(summary.contains("×3"));
    }

    @Test
    public void getSummary_swipe_returnsStartEndAndDuration() {
        TaskStep step = new TaskStep();
        step.setActionType(TaskActionType.SWIPE);
        step.setX(100);
        step.setY(200);
        step.setEndX(300);
        step.setEndY(400);
        step.setDurationMs(500);

        String summary = step.getSummary();
        assertTrue(summary.contains("(100,200)"));
        assertTrue(summary.contains("(300,400)"));
        assertTrue(summary.contains("500ms"));
    }

    @Test
    public void getSummary_textInput_returnsCharCount() {
        TaskStep step = new TaskStep();
        step.setActionType(TaskActionType.TEXT_INPUT);
        step.setTextContent("Hello World"); // 11 chars

        String summary = step.getSummary();
        assertTrue(summary.contains("11 字"));
    }

    @Test
    public void jsonRoundTrip_preservesAllFields() throws Exception {
        TaskStep step = new TaskStep();
        step.setId("test-id-123");
        step.setName("序列化测试");
        step.setEnabled(true);
        step.setActionType(TaskActionType.SWIPE);
        step.setOrder(3);
        step.setX(42);
        step.setY(84);
        step.setTapCount(2);
        step.setEndX(128);
        step.setEndY(256);
        step.setDurationMs(300);
        step.setClickIntervalMs(150);
        step.setPressDurationMs(50);
        step.setAutoFocusBeforeInput(true);
        step.setBeforeDelayMs(500);

        org.json.JSONObject json = step.toJson();
        TaskStep restored = TaskStep.fromJson(json);

        assertEquals("test-id-123", restored.getId());
        assertEquals("序列化测试", restored.getName());
        assertTrue(restored.isEnabled());
        assertEquals(TaskActionType.SWIPE, restored.getActionType());
        assertEquals(3, restored.getOrder());
        assertEquals(42, restored.getX());
        assertEquals(84, restored.getY());
        assertEquals(128, restored.getEndX());
        assertEquals(256, restored.getEndY());
        assertEquals(300, restored.getDurationMs());
        assertEquals(150, restored.getClickIntervalMs());
        assertEquals(50, restored.getPressDurationMs());
        assertTrue(restored.isAutoFocusBeforeInput());
        assertEquals(500, restored.getBeforeDelayMs());
    }

    @Test
    public void noMoreWaitActionType() {
        // 确保 WAIT 类型已从枚举中移除
        for (TaskActionType type : TaskActionType.values()) {
            assertNotEquals("WAIT", type.name());
        }
    }
}
