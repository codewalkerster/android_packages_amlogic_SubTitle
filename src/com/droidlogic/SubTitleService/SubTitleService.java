package com.droidlogic.SubTitleService;

import android.util.Log;
import android.util.DisplayMetrics;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import java.lang.ref.WeakReference;
import android.view.*;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.View.OnClickListener;
import android.widget.AdapterView.OnItemClickListener;
import android.graphics.*;
import com.subtitleparser.*;
import com.subtitleview.SubtitleView;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.app.AlertDialog;
import android.webkit.URLUtil;
import android.widget.*;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.RuntimeException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubTitleService extends ISubTitleService.Stub {
    private static final String TAG = "SubTitleService";
    private static final String SUBTITLE_WIN_TITLE = "com.droidlogic.subtitle.win";
    private static final String DISPLAY_MODE_SYSFS = "/sys/class/display/mode";

    private static final int OPEN = 0xF0; //random value
    private static final int SHOW_CONTENT = 0xF1;
    private static final int CLOSE = 0xF2;
    private static final int OPT_SHOW = 0xF3;
    private static final int SET_TXT_COLOR = 0xF4;
    private static final int SET_TXT_SIZE = 0xF5;
    private static final int SET_TXT_STYLE = 0xF6;
    private static final int SET_GRAVITY = 0xF7;
    private static final int SET_POS_HEIGHT = 0xF8;
    private static final int HIDE = 0xF9;
    private static final int DISPLAY = 0xFA;
    private static final int CLEAR = 0xFB;
    private static final int RESET_FOR_SEEK = 0xFC;
    private static final int LOAD = 0xFD;
    private static final int SUB_OFF = 0;
    private static final int SUB_ON = 1;
    private int subShowState = SUB_OFF;

    private boolean mDebug = SystemProperties.getBoolean("sys.subtitleService.debug", true);
    private Context mContext;
    private View mSubView;
    private Display mDisplay;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mWindowLayoutParams;
    private boolean isViewAdded = false;

    //for subtitle
    private SubtitleUtils mSubtitleUtils;
    private SubtitleView subTitleView = null;
    private int mSubTotal = -1;
    private int mCurSubId = 0;

    //for subtitle option
    private AlertDialog mOptionDialog;
    private ListView mListView;
    private int mCurOptSelect = 0;

    //for subtitle img ratio
    private boolean mRatioSet = false;

    public SubTitleService(Context context) {
        LOGI("[SubTitleService]");
        mContext = context;
        init();
    }

    private void LOGI(String msg) {
        if (mDebug) Log.i(TAG, msg);
    }

    private void init() {
        //init view
        mSubView = LayoutInflater.from(mContext).inflate(R.layout.subtitleview, null);
        subTitleView = (SubtitleView) mSubView.findViewById(R.id.subtitle);
        subTitleView.clear();
        subTitleView.setTextColor(Color.WHITE);
        subTitleView.setTextSize(20);
        subTitleView.setTextStyle(Typeface.NORMAL);
        subTitleView.setViewStatus(true);

        //prepare window
        mWindowManager = (WindowManager)mContext.getSystemService (Context.WINDOW_SERVICE);
        mDisplay = mWindowManager.getDefaultDisplay();
        mWindowLayoutParams = new WindowManager.LayoutParams();
        mWindowLayoutParams.type = LayoutParams.TYPE_SYSTEM_OVERLAY;
        mWindowLayoutParams.format = PixelFormat.TRANSLUCENT;
        mWindowLayoutParams.flags = LayoutParams.FLAG_NOT_TOUCH_MODAL
                  | LayoutParams.FLAG_NOT_FOCUSABLE
                  | LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        mWindowLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        mWindowLayoutParams.setTitle(SUBTITLE_WIN_TITLE);
        mWindowLayoutParams.x = 0;
        mWindowLayoutParams.y = 0;
        mWindowLayoutParams.width = mDisplay.getWidth();
        mWindowLayoutParams.height = mDisplay.getHeight();
    }

    private void addView() {
        LOGI("[addView]isViewAdded:" + isViewAdded);
        if (!isViewAdded) {
            mWindowManager.addView(mSubView, mWindowLayoutParams);
            isViewAdded = true;
        }
    }

    private void removeView() {
        LOGI("[removeView]isViewAdded:" + isViewAdded);
        if (isViewAdded) {
            mWindowManager.removeView(mSubView);
            isViewAdded = false;
        }
    }

    public void open (String path) {
        LOGI("[open] path: " + path);
        if (mOptionDialog != null) {
            mOptionDialog.dismiss();
        }

        if (path != null && !path.equals("")) {
            File file = new File(path);
            String name = file.getName();
            if (name == null || (name != null && -1 == name.lastIndexOf('.'))) {
                return;
            }
            mSubtitleUtils = new SubtitleUtils(path);
        }
        else {
            mSubtitleUtils = new SubtitleUtils();
        }

        mSubTotal = mSubtitleUtils.getSubTotal();
        mCurSubId = mSubtitleUtils.getCurrentInSubtitleIndexByJni(); //get inner subtitle current index as default, 0 is always, if there is no inner subtitle, 0 indicate the first external subtitle
        LOGI("[open] mCurSubId: " + mCurSubId);
        sendOpenMsg(mCurSubId);

        //load("http://milleni.ercdn.net/9_test/double_lang_test.xml"); for test
        //sendOptionMsg();
    }

    public void close() {
        LOGI("[close]");
        if (mSubtitleUtils != null) {
            mSubtitleUtils.setSubtitleNumber(0);
            mSubtitleUtils = null;
        }
        mSubTotal = -1;
        sendCloseMsg();
    }

    public int getSubTotal() {
        LOGI("[getSubTotal] mSubTotal:" + mSubTotal);
        return mSubTotal;
    }

    public int getInnerSubTotal() {
        return mSubtitleUtils.getInSubTotal();
    }

    public int getExternalSubTotal() {
        return mSubtitleUtils.getExSubTotal();
    }

    public void nextSub() { // haven't test
        LOGI("[nextSub]mCurSubId:" + mCurSubId + ",mSubTotal:" + mSubTotal);
        if (mSubTotal > 0) {
            mCurSubId++;
            if (mCurSubId >= mSubTotal) {
                mCurSubId = 0;
            }
            sendOpenMsg(mCurSubId);
        }
    }

    public void preSub() { // haven't test
        LOGI("[preSub]mCurSubId:" + mCurSubId + ",mSubTotal:" + mSubTotal);
        if (mSubTotal > 0) {
            mCurSubId--;
            if (mCurSubId < 0) {
                mCurSubId = mSubTotal - 1;
            }
            sendOpenMsg(mCurSubId);
        }
    }

    public void openIdx(int idx) {
        LOGI("[openIdx]idx:" + idx);
        if (idx >= 0 && idx < mSubTotal) {
            mCurSubId = idx;
            sendOpenMsg(idx);
        }
    }

    public void showSub(int position) {
        LOGI("[showSub]position:" + position);
        if (position >= 0) {
            sendShowSubMsg(position);
        }
    }

    public int getSubType() {
        return subTitleView.getSubType();
    }

    public String getSubTypeStr() {
        return subTitleView.getSubTypeStr();
    }

    public int getSubTypeDetial() {
        return subTitleView.getSubTypeDetial();
    }

    public void setTextColor (int color) {
        sendSetTxtColorMsg(color);
    }

    public void setTextSize (int size) {
        sendSetTxtSizeMsg(size);
    }

    public void setTextStyle (int style) {
        sendSetTxtStyleMsg(style);
    }

    public void setGravity (int gravity) {
        sendSetGravityMsg(gravity);
    }

    public void setPosHeight (int height) {
        sendSetPosHeightMsg(height);
    }

    public void hide() {
        sendHideMsg();
    }

    public void display() {
        sendDisplayMsg();
    }

    public void clear() {
        sendClearMsg();
    }

    public void resetForSeek() {
        sendResetForSeekMsg();
    }

    public void option() {
        sendOptionMsg();
    }

    public void setImgSubRatio (float ratioW, float ratioH, int maxW, int maxH) {
        LOGI("[setImgSubRatio] ratioW:" + ratioW + ", ratioH:" + ratioH + ",maxW:" + maxW + ",maxH:" + maxH);
        //subTitleView.setImgSubRatio(ratioW, ratioH, maxW, maxH);
    }

    public String getCurName() {
        SubID subID = mSubtitleUtils.getSubID(mCurSubId);
        if (subID != null) {
            LOGI("[getCurName]name:" + subID.filename);
            return subID.filename;
        }
        return null;
    }

    public String getSubName(int idx) {
        String name = null;

        if (idx >= 0 && idx < mSubTotal && mSubtitleUtils != null) {
            name = mSubtitleUtils.getSubPath(idx);
            if (name != null) {
                int index = name.lastIndexOf("/");
                if (index >= 0) {
                    name = name.substring(index + 1);
                }
                else {
                    if (name.equals("INSUB")) {
                        name = mSubtitleUtils.getInSubName(idx);
                    }
                }
            }
        }
        LOGI("[getSubName]idx:" + idx + ",name:" + name);
        return name;
    }

    public String getSubLanguage(int idx) {
        LOGI("[getSubLanguage]idx:" + idx);
        String language = null;
        int index = 0;

        if (idx > 0 && idx < mSubTotal && mSubtitleUtils != null) {
            language = mSubtitleUtils.getSubPath(idx);
            if (language != null) {
                index = language.lastIndexOf(".");
                if (index >= 0) {
                    language = language.substring(0, index);
                    index = language.lastIndexOf(".");
                    if (index >= 0) {
                        language = language.substring(index + 1);
                    }
                }
            }
            if (language.equals("INSUB")) {
                language = mSubtitleUtils.getInSubLanguage(idx);
            }
        }
        if (language != null) { // if no language, getSubName (skip file path)
            index = language.lastIndexOf("/");
            if (index >= 0) {
                language = getSubName(idx);
            }
        }
        LOGI("[getSubLanguage] idx:" + idx + ",language:" + language);
        return language;
    }

    private void sendOpenMsg(int idx) {
        if (mSubtitleUtils != null) {
            Message msg = mHandler.obtainMessage(OPEN);
            SubID subID = mSubtitleUtils.getSubID(idx);
            if (subID != null) {
                msg.obj = subID;
                mHandler.sendMessage(msg);
            }
        }
    }

    private void sendCloseMsg() {
        Message msg = mHandler.obtainMessage(CLOSE);
        mHandler.sendMessage(msg);
    }

    private void sendShowSubMsg(int pos) {
        Message msg = mHandler.obtainMessage(SHOW_CONTENT);
        msg.arg1 = pos;
        mHandler.sendMessage(msg);
    }

    private void sendOptionMsg() {
        Message msg = mHandler.obtainMessage(OPT_SHOW);
        if (mSubTotal > 0) {
            mHandler.sendMessage(msg);
        }
    }

    private void sendLoadMsg(String path) {
        Message msg = mHandler.obtainMessage(LOAD);
        if (path != null) {
            msg.obj = path;
            mHandler.sendMessage(msg);
        }
    }

    private void sendSetTxtColorMsg(int color) {
        Message msg = mHandler.obtainMessage(SET_TXT_COLOR);
        msg.arg1 = color;
        mHandler.sendMessage(msg);
    }

    private void sendSetTxtSizeMsg(int size) {
        Message msg = mHandler.obtainMessage(SET_TXT_SIZE);
        msg.arg1 = size;
        mHandler.sendMessage(msg);
    }

    private void sendSetTxtStyleMsg(int style) {
        Message msg = mHandler.obtainMessage(SET_TXT_STYLE);
        msg.arg1 = style;
        mHandler.sendMessage(msg);
    }

    private void sendSetGravityMsg(int gravity) {
        Message msg = mHandler.obtainMessage(SET_GRAVITY);
        msg.arg1 = gravity;
        mHandler.sendMessage(msg);
    }

    private void sendSetPosHeightMsg(int height) {
        Message msg = mHandler.obtainMessage(SET_POS_HEIGHT);
        msg.arg1 = height;
        mHandler.sendMessage(msg);
    }

    private void sendHideMsg() {
        Message msg = mHandler.obtainMessage (HIDE);
        mHandler.sendMessage(msg);
    }

    private void sendDisplayMsg() {
        Message msg = mHandler.obtainMessage(DISPLAY);
        mHandler.sendMessage(msg);
    }

    private void sendClearMsg() {
        Message msg = mHandler.obtainMessage(CLEAR);
        mHandler.sendMessage(msg);
    }

    private void sendResetForSeekMsg() {
        Message msg = mHandler.obtainMessage (RESET_FOR_SEEK);
        mHandler.sendMessage(msg);
    }

    private void removeMsg() {
        mHandler.removeMessages(SHOW_CONTENT);
        mHandler.removeMessages(OPT_SHOW);
        mHandler.removeMessages(OPEN);
        mHandler.removeMessages(LOAD);
    }

    //http://milleni.ercdn.net/9_test/double_lang_test.xml
    private String downloadXmlFile(String strURL) {
        String filePath = null;

        //"/storage/sdcard0/Download";
        String dirPath = Environment.getExternalStorageDirectory().getPath() + "/" + Environment.DIRECTORY_DOWNLOADS;
        File baseFile = new File(dirPath);
        if (!baseFile.isDirectory() && !baseFile.mkdir() ) {
            Log.e(TAG, "[downloadXmlFile] unable to create external downloads directory " + baseFile.getPath());
            return null;
        }

        try {
            if (!URLUtil.isNetworkUrl(strURL)) {
                LOGI("[downloadXmlFile] is not network Url, strURL: " + strURL);
            } else {
                URL myURL = new URL(strURL);
                URLConnection conn = myURL.openConnection();
                conn.connect();
                InputStream is = conn.getInputStream();
                if (is == null) {
                    Log.e(TAG, "[downloadXmlFile] stream is null");
                }

                String fileName = strURL.substring(strURL.lastIndexOf("/") + 1);
                if (fileName != null) {
                    filePath = dirPath + "/" + fileName;
                    LOGI("[downloadXmlFile] filePath: " + filePath);
                    File file = new File(filePath);
                    if (!file.exists()) {
                        file.createNewFile();
                    } else {
                        file.delete();
                    }
                    FileOutputStream fos = new FileOutputStream(filePath);
                    byte buf[] = new byte[128];
                    do {
                        int numread = is.read(buf);
                        if (numread <= 0) {
                            break;
                        }
                        fos.write(buf, 0, numread);
                    } while (true);
                    is.close();
                    fos.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return filePath;
    }

    public boolean load(String path) {
        boolean ret = false;
        final String urlPath = path; // for subtitle which should download from net

        if ((path.startsWith("http://") || path.startsWith("https://")) && (path.endsWith("xml"))) {
            ret = true; // url subtitle return true
            Runnable r = new Runnable() {
                public void run() {
                    try {
                        String pathTmp = downloadXmlFile(urlPath);
                        String pathExtTmp = pathTmp.substring(pathTmp.lastIndexOf ('.') + 1);
                        //LOGI("[load] pathExtTmp: " + pathExtTmp + ", pathTmp:" + pathTmp);
                        for (String ext : SubtitleUtils.extensions) {
                            if (pathExtTmp.toLowerCase().equals(ext)) {
                                sendLoadMsg(pathTmp);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        Log.e (TAG, e.getMessage(), e);
                    }
                }
            };
            new Thread (r).start();
        } else {
            String pathExt = path.substring(path.lastIndexOf ('.') + 1);
            //LOGI("[load] pathExt: " + pathExt + ", path:" + path);
            for (String ext : SubtitleUtils.extensions) {
                if (pathExt.toLowerCase().equals(ext) ) {
                    sendLoadMsg(path);
                    ret = true;
                    break;
                }
            }
        }
        return ret;
    }

    private void adjustImgRatioDft() {
        float ratio = 1.000f;
        float ratioMax = 2.000f;
        float ratioMin = 0.800f;

        if (mRatioSet) {
            return;
        }

        int originW = subTitleView.getOriginW();
        int originH = subTitleView.getOriginH();
        LOGI("[adjustImgRatioDft] originW: " + originW + ", originH:" + originH);
        if (originW <= 0 || originH <= 0) {
            return;
        }

        //String mode = mSystemWriteManager.readSysfs(DISPLAY_MODE_SYSFS).replaceAll("\n","");
        //int[] curPosition = mMboxOutputModeManager.getPosition(mode);
        //int modeW = curPosition[2];
        //int modeH = curPosition[3];
        //LOGI("[adjustImgRatioDft] modeW: " + modeW + ", modeH:" + modeH);
        DisplayMetrics dm = new DisplayMetrics();
        dm = mContext.getResources().getDisplayMetrics();
        int frameW = dm.widthPixels;
        int frameH = dm.heightPixels;
        LOGI("[adjustImgRatioDft] frameW: " + frameW + ", frameH:" + frameH);

        if (frameW > 0 && frameH > 0) {
            float ratioW = ((float)frameW / (float)originW);
            float ratioH = ((float)frameH / (float)originH);
            LOGI("[adjustImgRatioDft] ratioW: " + ratioW + ", ratioH:" + ratioH);
            if (ratioW > ratioH) {
                ratio = ratioH;
            }
            else if (ratioW <= ratioH) {
                ratio = ratioW;
            }

            if (ratio >= ratioMax) {
                ratio = ratioMax;
            }
            else if (ratio <= ratioMin) {
                ratio = ratioMin;
            }
        }
        LOGI("[adjustImgRatioDft] ratio: " + ratio);
        if (ratio > 0) {
            subTitleView.setImgSubRatio(ratio);
        }
        mRatioSet = true;
    }

    private String setSublanguage() {
        String type = null;
        String able = mContext.getResources().getConfiguration().locale.getCountry();
        if (able.equals("TW")) {
            type = "BIG5";
        } else if (able.equals("JP")) {
            type = "cp932";
        } else if (able.equals("KR")) {
            type = "cp949";
        } else if (able.equals("IT") || able.equals("FR") || able.equals("DE")) {
            type = "iso88591";
        } else if (able.equals("TR")) {
            type = "cp1254";
        } else if (able.equals("PC")) {
            type = "cp1098";// "cp1097";
        } else {
            type = "GBK";
        }
        return type;
    }

    private void openFile(SubID subID) {
        LOGI("[openFile] subID: " + subID);
        if (subID == null) {
            return;
        }

        try {
            if (subTitleView.setFile(subID, setSublanguage()) == Subtitle.SUBTYPE.SUB_INVALID) {
                return;
            }
            if (mSubtitleUtils != null) {
                mSubtitleUtils.setSubtitleNumber(subID.index);
            }
        } catch (Exception e) {
            Log.e(TAG, "open:error");
            e.printStackTrace();
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        LOGI("[dump]fd:" + fd.getInt$() + ",mSubtitleUtils:" + mSubtitleUtils);
        if (mSubtitleUtils != null) {
            mSubtitleUtils.nativeDump(fd.getInt$());
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage (Message msg) {
            LOGI("[handleMessage]msg.what:" + msg.what + ",msg.arg1:" + msg.arg1 + ",subShowState:" + subShowState);

            switch (msg.what) {
                case SHOW_CONTENT:
                    if (subShowState == SUB_ON) {
                        addView();
                        adjustImgRatioDft();
                        subTitleView.tick (msg.arg1);
                    }
                    break;

                case OPEN:
                    if (subShowState == SUB_ON) {
                        removeView();
                        subTitleView.stopSubThread(); //close insub parse thread
                        subTitleView.closeSubtitle();
                        subTitleView.clear();
                        mRatioSet = false;
                    }

                    subTitleView.startSubThread(); //open insub parse thread
                    openFile((SubID)msg.obj);
                    subTitleView.setVisibility(View.VISIBLE);
                    subShowState = SUB_ON;
                    break;

                case CLOSE:
                    if (subShowState == SUB_ON) {
                        removeView();
                        subTitleView.stopSubThread(); //close insub parse thread
                        subTitleView.closeSubtitle();
                        subTitleView.clear();
                        subShowState = SUB_OFF;
                        mRatioSet = false;
                    }
                    break;

                case SET_TXT_COLOR:
                    subTitleView.setTextColor(msg.arg1);
                    break;

                case SET_TXT_SIZE:
                    subTitleView.setTextSize(msg.arg1);
                    break;

                case SET_TXT_STYLE:
                    subTitleView.setTextStyle(msg.arg1);
                    break;

                case SET_GRAVITY:
                    subTitleView.setGravity(msg.arg1);
                    break;

                case SET_POS_HEIGHT:
                    subTitleView.setPadding (
                        subTitleView.getPaddingLeft(),
                        subTitleView.getPaddingTop(),
                        subTitleView.getPaddingRight(),
                        msg.arg1);
                    break;

                case HIDE:
                    if (View.VISIBLE == subTitleView.getVisibility()) {
                        subTitleView.setVisibility (View.GONE);
                        subShowState = SUB_OFF;
                    }
                    break;

                case DISPLAY:
                    if (View.VISIBLE != subTitleView.getVisibility()) {
                        subTitleView.setVisibility (View.VISIBLE);
                        subShowState = SUB_ON;
                    }
                    break;

                case CLEAR:
                    subTitleView.clear();
                    break;

                case RESET_FOR_SEEK:
                    subTitleView.resetForSeek();
                    break;

                case OPT_SHOW:
                    showOptionOverlay();
                    break;

                case LOAD:
                    if (subShowState == SUB_ON) {
                        removeView();
                        subTitleView.stopSubThread(); //close insub parse thread
                        subTitleView.closeSubtitle();
                        subTitleView.clear();
                        mRatioSet = false;
                    }

                    try {
                        subTitleView.loadSubtitleFile((String)msg.obj, setSublanguage());
                        subShowState = SUB_ON;
                    } catch (Exception e) {
                        Log.e(TAG, "load:error");
                        e.printStackTrace();
                    }
                    break;

                default:
                    Log.e(TAG, "[handleMessage]error msg.");
                    break;
            }
        }
    };

    private void showOptionOverlay() {
        LOGI("[showOptionOverlay]");

        //show dialog
        View view = View.inflate(mContext, R.layout.option, null);
        AlertDialog.Builder builder = new AlertDialog.Builder (mContext);
        builder.setView(view);
        builder.setTitle(R.string.option_title_str);
        mOptionDialog = builder.create();
        mOptionDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        mOptionDialog.show();

        //adjust Attributes
        LayoutParams lp = mOptionDialog.getWindow().getAttributes();
        int w = mDisplay.getWidth();
        int h = mDisplay.getHeight();
        if (h > w) {
            lp.width = (int) (w * 1.0);
        } else {
            lp.width = (int) (w * 0.5);
        }
        mOptionDialog.getWindow().setAttributes(lp);

        mListView = (ListView) view.findViewById(R.id.list_view);
        SimpleAdapter adapter = new SimpleAdapter(mContext,
            getListData(),
            R.layout.list_item,
            new String[] {"item_text", "item_img"},
            new int[] {R.id.item_text, R.id.item_img});
        mListView.setAdapter(adapter);

        /*set listener */
        mListView.setOnItemClickListener (new OnItemClickListener() {
            public void onItemClick (AdapterView<?> parent, View view, int pos, long id) {
                LOGI("[option select]select subtitle " + (pos - 1) );
                if (pos == 0) { //first is close subtitle showing
                    sendHideMsg();
                } else if (pos > 0) {
                    mCurSubId = (pos - 1);
                    sendOpenMsg(pos - 1);
                }
                mCurOptSelect = pos;
                updateListDisplay();
                mOptionDialog.dismiss();
            }
        });
    }

    private List<Map<String, Object>> getListData() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        boolean clsItmAdded = false;
        String trackStr = mContext.getResources().getString (R.string.opt_sub_track);
        String closeStr = mContext.getResources().getString (R.string.opt_close);

        for (int i = 0; i < mSubTotal; i++) {
            if (!clsItmAdded) {
                //add close subtitle item
                Map<String, Object> mapCls = new HashMap<String, Object>();
                clsItmAdded = true;
                mapCls.put ("item_text", closeStr);
                if (mCurOptSelect == 0) {
                    mapCls.put("item_img", R.drawable.item_img_sel);
                } else {
                    mapCls.put("item_img", R.drawable.item_img_unsel);
                }
                list.add (mapCls);
            }

            Map<String, Object> map = new HashMap<String, Object>();
            String subTrackStr = trackStr + Integer.toString(i);
            map.put ("item_text", subTrackStr);
            //LOGI("[getListData]map.put[" + i + "]:" + subTrackStr + ",mCurOptSelect:" + mCurOptSelect);

            if (mCurOptSelect == (i + 1)) {
                map.put("item_img", R.drawable.item_img_sel);
            } else {
                map.put("item_img", R.drawable.item_img_unsel);
            }
            list.add(map);
        }
        return list;
    }

    private void updateListDisplay() {
        Map<String, Object> list_item;
        for (int i = 0; i < mListView.getAdapter().getCount(); i++) {
            list_item = (Map<String, Object>) mListView.getAdapter().getItem(i);
            if (mCurOptSelect == i) {
                list_item.put("item_img", R.drawable.item_img_sel);
            } else {
                list_item.put("item_img", R.drawable.item_img_unsel);
            }
        }
        ((BaseAdapter) mListView.getAdapter()).notifyDataSetChanged();
    }
}
