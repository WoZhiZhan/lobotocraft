package com.wzz.lobotocraft.natives.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;

/**
 * Windows User32 API JNA接口
 */
public interface User32 extends Library {

    User32 INSTANCE = Native.load("user32", User32.class);

    /**
     * 创建消息框
     * @param hWnd 父窗口句柄
     * @param lpText 消息内容
     * @param lpCaption 标题
     * @param uType 类型和图标
     * @return 用户点击的按钮ID
     */
    int MessageBoxW(HWND hWnd, WString lpText, WString lpCaption, int uType);

    /**
     * 查找窗口
     * @param lpClassName 类名（null表示任意）
     * @param lpWindowName 窗口标题
     * @return 窗口句柄
     */
    HWND FindWindowW(WString lpClassName, WString lpWindowName);

    /**
     * 设置窗口位置和大小
     * @param hWnd 窗口句柄
     * @param hWndInsertAfter 插入位置
     * @param X X坐标
     * @param Y Y坐标
     * @param cx 宽度
     * @param cy 高度
     * @param uFlags 标志
     * @return 成功返回true
     */
    boolean SetWindowPos(HWND hWnd, HWND hWndInsertAfter,
                         int X, int Y, int cx, int cy, int uFlags);

    /**
     * 闪烁窗口
     * @param hWnd 窗口句柄
     * @param bInvert 是否反转
     * @return 成功返回true
     */
    boolean FlashWindow(HWND hWnd, boolean bInvert);

    /**
     * 发送消息到窗口（同步）
     * @param hWnd 窗口句柄
     * @param Msg 消息ID
     * @param wParam 参数1
     * @param lParam 参数2
     * @return 消息处理结果
     */
    LRESULT SendMessage(HWND hWnd, int Msg, WPARAM wParam, LPARAM lParam);

    /**
     * 投递消息到窗口（异步）
     * @param hWnd 窗口句柄
     * @param Msg 消息ID
     * @param wParam 参数1
     * @param lParam 参数2
     * @return 成功返回true
     */
    boolean PostMessage(HWND hWnd, int Msg, WPARAM wParam, LPARAM lParam);

    /**
     * 销毁窗口
     * @param hWnd 窗口句柄
     * @return 成功返回true
     */
    boolean DestroyWindow(HWND hWnd);

    /**
     * 检查窗口句柄是否有效
     * @param hWnd 窗口句柄
     * @return 窗口存在返回true
     */
    boolean IsWindow(HWND hWnd);

    HWND GetDlgItem(HWND hDlg, int nIDDlgItem);

    /**
     * 将窗口设置为前台窗口
     * @param hWnd 窗口句柄
     * @return 成功返回true，失败返回false
     */
    boolean SetForegroundWindow(HWND hWnd);

    /**
     * 获取前台窗口句柄
     * @return 前台窗口句柄
     */
    HWND GetForegroundWindow();

    /**
     * 获取活动窗口句柄
     * @return 活动窗口句柄
     */
    HWND GetActiveWindow();

    /**
     * 设置焦点到指定窗口
     * @param hWnd 窗口句柄
     * @return 之前拥有焦点的窗口句柄
     */
    HWND SetFocus(HWND hWnd);

    /**
     * 附加线程输入（允许跨线程设置前台窗口）
     * @param idAttach 要附加到的线程ID
     * @param idAttachTo 当前线程ID
     * @param fAttach true附加，false分离
     * @return 成功返回true
     */
    boolean AttachThreadInput(int idAttach, int idAttachTo, boolean fAttach);

    /**
     * 获取窗口线程ID
     * @param hWnd 窗口句柄
     * @param lpdwProcessId 接收进程ID（可null）
     * @return 线程ID
     */
    int GetWindowThreadProcessId(HWND hWnd, int[] lpdwProcessId);
}