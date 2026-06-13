package com.wzz.lobotocraft.natives.jna;

/**
 * Windows User32 API常量定义
 * 补充DisplayMessage需要的常量
 */
public interface WinUser {
    
    // MessageBox按钮类型
    int MB_OK = 0x00000000;
    int MB_OKCANCEL = 0x00000001;
    int MB_YESNO = 0x00000004;
    
    // MessageBox图标类型
    int MB_ICONERROR = 0x00000010;
    int MB_ICONWARNING = 0x00000030;
    int MB_ICONINFORMATION = 0x00000040;
    int MB_ICONQUESTION = 0x00000020;
    
    // MessageBox模态类型
    int MB_TOPMOST = 0x00040000;
    int MB_SYSTEMMODAL = 0x00001000;
    
    // SetWindowPos标志
    int SWP_NOSIZE = 0x0001;
    int SWP_NOMOVE = 0x0002;
    int SWP_NOZORDER = 0x0004;
    int SWP_SHOWWINDOW = 0x0040;
    
    // 窗口消息
    int WM_CLOSE = 0x0010;
    int WM_DESTROY = 0x0002;
    int WM_QUIT = 0x0012;
    
    // ========== TaskDialog常量 ==========
    
    // TaskDialog flags
    int TDF_ENABLE_HYPERLINKS = 0x0001;
    int TDF_USE_HICON_MAIN = 0x0002;
    int TDF_USE_HICON_FOOTER = 0x0004;
    int TDF_ALLOW_DIALOG_CANCELLATION = 0x0008;
    int TDF_USE_COMMAND_LINKS = 0x0010;
    int TDF_USE_COMMAND_LINKS_NO_ICON = 0x0020;
    int TDF_EXPAND_FOOTER_AREA = 0x0040;
    int TDF_EXPANDED_BY_DEFAULT = 0x0080;
    int TDF_VERIFICATION_FLAG_CHECKED = 0x0100;
    int TDF_SHOW_PROGRESS_BAR = 0x0200;
    int TDF_SHOW_MARQUEE_PROGRESS_BAR = 0x0400;
    int TDF_CALLBACK_TIMER = 0x0800;
    int TDF_POSITION_RELATIVE_TO_WINDOW = 0x1000;
    int TDF_RTL_LAYOUT = 0x2000;
    int TDF_NO_DEFAULT_RADIO_BUTTON = 0x4000;
    int TDF_CAN_BE_MINIMIZED = 0x8000;
    int TDF_SIZE_TO_CONTENT = 0x01000000;
    
    // TaskDialog common buttons
    int TDCBF_OK_BUTTON = 0x0001;
    int TDCBF_YES_BUTTON = 0x0002;
    int TDCBF_NO_BUTTON = 0x0004;
    int TDCBF_CANCEL_BUTTON = 0x0008;
    int TDCBF_RETRY_BUTTON = 0x0010;
    int TDCBF_CLOSE_BUTTON = 0x0020;
    
    // TaskDialog icons (TD_ERROR_ICON等)
    int TD_ERROR_ICON = 65534;         // -2
    int TD_WARNING_ICON = 65535;       // -1
    int TD_INFORMATION_ICON = 65533;   // -3
    int TD_SHIELD_ICON = 65532;        // -4
    
    // TaskDialog messages
    int TDM_CLICK_BUTTON = 0x0400 + 102;  // WM_USER + 102
    int TDM_ENABLE_BUTTON = 0x0400 + 111;
    int TDM_ENABLE_RADIO_BUTTON = 0x0400 + 112;
    int TDM_CLICK_RADIO_BUTTON = 0x0400 + 110;
    int TDM_CLICK_VERIFICATION = 0x0400 + 113;
    int TDM_UPDATE_ELEMENT_TEXT = 0x0400 + 114;
    int TDM_SET_BUTTON_ELEVATION_REQUIRED_STATE = 0x0400 + 115;
    int TDM_UPDATE_ICON = 0x0400 + 116;
}