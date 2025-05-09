package caa.component.member;

/**
 * 表示数据表显示模式的枚举
 * 用于替代DatatablePanel中的布尔flag参数
 */
public enum DisplayMode {
    /**
     * 标准显示模式 - 不显示计数
     */
    STANDARD,

    /**
     * 计数显示模式 - 显示计数信息
     */
    COUNT
}