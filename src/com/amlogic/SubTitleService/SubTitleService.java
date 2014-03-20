package com.amlogic.SubTitleService;

import android.util.Log;
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
import android.os.Handler; 
import android.os.Message;
import android.app.AlertDialog;
import android.widget.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubTitleService extends Service {
    private static final String TAG = "SubTitleService";
    private boolean DEBUG = false;

    private int mServiceStartId = -1;
    private boolean mServiceInUse = false;

    private Context mContext;
    private View mSubView = null;
    private WindowManager mWm = null;
    WindowManager.LayoutParams p;

    //for subtitle
    private SubtitleUtils subtitleUtils = null;
    private SubtitleView subTitleView = null;
    private int subTotal= 0;
    private int curSubId = 0;
    private SubID subID;

    //for subtitle option
    private AlertDialog d;
    private ListView lv;
    private int curOptSelect = 0; 

    private static final int THREAD_STOP = 0;
    private static final int THREAD_RUN = 1;
    private int  subThreadState = THREAD_STOP;
    private static final int OPEN = 0xF0; //random value 
    private static final int SHOW_CONTENT = 0xF1;
    private static final int CLOSE = 0xF2; 
    private static final int OPT_SHOW = 0xF3; 
    private static final int INITSELECT = 0xF4;
    private static final int SET_TXT_COLOR = 0xF5;
    private static final int SET_TXT_SIZE = 0xF6;
    private static final int SET_TXT_STYLE = 0xF7;
    private static final int SET_GRAVITY = 0xF8;
    private static final int SET_POS_HEIGHT = 0xF9;
    private static final int HIDE = 0xFA;
    private static final int DISPLAY = 0xFB;
    private static final int CLEAR = 0xFC;
    private static final long MSG_SEND_DELAY = 0; //0s
    private static final int SUB_OFF = 0;
    private static final int SUB_ON = 1;
    private int subShowState = SUB_OFF;

    private boolean subtitleServiceInited = false;

    @Override
    public void onCreate() {
        super.onCreate();
        if(DEBUG) Log.i(TAG,"[onCreate]");

        initView();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(DEBUG) Log.i(TAG,"[onStartCommand]");
        
        mServiceStartId = startId;


        return START_NOT_STICKY;//START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(DEBUG) Log.i(TAG,"[onDestroy]");

        subtitleServiceInited = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if(DEBUG) Log.i(TAG,"[onBind]");
    
        mServiceInUse = true;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        if(DEBUG) Log.i(TAG,"[onRebind]");

    
        mServiceInUse = true;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if(DEBUG) Log.i(TAG,"[onUnbind]");
        
        mServiceInUse = false;
        unregisterConfigurationChangeReceiver();
        stopSelf(mServiceStartId);
        System.exit(0);
        return true;
    }

    public void init() {
        if(DEBUG) Log.i(TAG, "[init]");
        if(subtitleServiceInited == false) {
            subtitleServiceInited = true;
            showSubtitleOverlay();
        }
    }

    private void initView() {
        mContext = SubTitleService.this;
        mSubView = LayoutInflater.from(mContext).inflate(R.layout.subtitleview, null);
        subTitleView = (SubtitleView) mSubView.findViewById(R.id.subtitle);
        subTitleView.clear();
        subTitleView.setTextColor(Color.WHITE);
        subTitleView.setTextSize(28);
        subTitleView.setTextStyle(Typeface.BOLD);
        subTitleView.setViewStatus(true);
        registerConfigurationChangeReceiver();
    }

    private void showSubtitleOverlay() {
        if(DEBUG) Log.i(TAG,"[showSubtitleOverlay]");
        
        mWm = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        p = new WindowManager.LayoutParams();
        p.type = LayoutParams.TYPE_SYSTEM_OVERLAY ;
        p.format = PixelFormat.TRANSLUCENT;
        p.flags = LayoutParams.FLAG_NOT_TOUCH_MODAL
            | LayoutParams.FLAG_NOT_FOCUSABLE
            | LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        p.gravity = Gravity.LEFT | Gravity.TOP; 
        Display display = mWm.getDefaultDisplay();
        //DisplayInfo displayinfo = new DisplayInfo();
        //display.getDisplayInfo(displayinfo);
        int mWScreenx = display.getWidth();
        int mWScreeny = display.getHeight();
        p.x = 0;
        p.y = 0;
        p.width = mWScreenx;//ViewGroup.LayoutParams.WRAP_CONTENT;
        p.height = mWScreeny;//ViewGroup.LayoutParams.WRAP_CONTENT;
        //if(DEBUG) Log.i(TAG,"[showSubtitleOverlay]mWm:"+mWm+",mSubView:"+mSubView);
        mWm.addView(mSubView, p);
    }

    private void registerConfigurationChangeReceiver() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED);
        registerReceiver(mConfigurationChangeReceiver, intentFilter);
        if(DEBUG) Log.i(TAG,"[registerConfigurationChangeReceiver]mConfigurationChangeReceiver:"+mConfigurationChangeReceiver);
    }

    private void unregisterConfigurationChangeReceiver() {
        if(DEBUG) Log.i(TAG,"[unregisterConfigurationChangeReceiver]mConfigurationChangeReceiver:"+mConfigurationChangeReceiver);
        if(mConfigurationChangeReceiver != null) {
            unregisterReceiver(mConfigurationChangeReceiver);
            mConfigurationChangeReceiver = null;
        }
    }

    private BroadcastReceiver mConfigurationChangeReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            updateSubWinLayoutParams();
        }
    };

    private void updateSubWinLayoutParams() {
        if(DEBUG) Log.i(TAG,"[updateSubtitleWinLayoutParams]subShowState:"+subShowState);
        if(subShowState == SUB_OFF)
            return;
        if(mWm == null)
            return;
        if(mSubView == null)
            return;
        if(p == null) 
            return;
        
        p.type = LayoutParams.TYPE_SYSTEM_OVERLAY ;
        p.format = PixelFormat.TRANSLUCENT;
        p.flags = LayoutParams.FLAG_NOT_TOUCH_MODAL
            | LayoutParams.FLAG_NOT_FOCUSABLE
            | LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        p.gravity = Gravity.LEFT | Gravity.TOP; 
        Display display = mWm.getDefaultDisplay();
        int mWScreenx = display.getWidth();
        int mWScreeny = display.getHeight();
        p.x = 0;
        p.y = 0;
        p.width = mWScreenx;//ViewGroup.LayoutParams.WRAP_CONTENT;
        p.height = mWScreeny;//ViewGroup.LayoutParams.WRAP_CONTENT;
        if(DEBUG) Log.i(TAG,"[updateSubtitleWinLayoutParams]p.width:"+p.width+",p.height:"+p.height);
        if(mWm != null) {
            mWm.removeView(mSubView);
        }
        mWm.addView(mSubView, p);
    }

    private void showOptionOverlay() {
        if(DEBUG) Log.i(TAG,"[showOptionOverlay]");

        int total = getSubTotal();
        if(total == 0) {
            Toast.makeText(mContext,
                mContext.getResources().getText(R.string.no_subtitle_str),
                Toast.LENGTH_SHORT).show(); 
            return;
        }
        
        View v = View.inflate(mContext, R.layout.option, null);  
        AlertDialog.Builder b = new AlertDialog.Builder(mContext);
        b.setView(v);
        b.setTitle(R.string.option_title_str);
        d = b.create();   
        d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        d.show();

       //adjust Attributes
        WindowManager wm = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);//getWindowManager();
        Display display = wm.getDefaultDisplay();
        LayoutParams lp = d.getWindow().getAttributes();
        if (display.getHeight() > display.getWidth()) {  
            lp.width = (int) (display.getWidth() * 1.0);          
        } 
        else {   
            lp.width = (int) (display.getWidth() * 0.5);                  
        }  
        d.getWindow().setAttributes(lp);

        lv = (ListView) v.findViewById(R.id.list_view);
        SimpleAdapter adapter = new SimpleAdapter(mContext, getListData(), R.layout.list_item,                 
                                                        new String[]{"item_text", "item_img"},                
                                                        new int[]{R.id.item_text, R.id.item_img});   
        lv.setAdapter(adapter);           

        /* set listener */  
        lv.setOnItemClickListener(new OnItemClickListener() {  
            public void onItemClick(AdapterView<?> parent, View view, int pos,  long id) {
                if(pos == 0) { //first is close subtitle showing
                    if(DEBUG) Log.i(TAG,"[option select]close subtitle showing");
                    sendHideMsg();
                }
                else if(pos > 0) {
                    if(DEBUG) Log.i(TAG,"[option select]select subtitle "+(pos-1));
                    curSubId = (pos-1);
                    sendCloseMsg(); // TODO: maybe have bug for opening the same subtitle after hide
                    sendOpenMsg();
                }

                curOptSelect = pos;
                updateListDisplay();
                //if(!DEBUG) d.dismiss();    
                d.dismiss();    
            }  
        });  
    }

    private List<Map<String, Object>> getListData() {    	
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();	
        boolean clsItmAdded = false;
        int total = getSubTotal();
        String trackStr = mContext.getResources().getString(R.string.opt_sub_track);
        String closeStr = mContext.getResources().getString(R.string.opt_close);
        if(DEBUG) Log.i(TAG,"[getListData]total:"+total);

        for(int i=0;i<total;i++) {
            if(!clsItmAdded) {
                //add close subtitle item
                Map<String, Object> mapCls = new HashMap<String, Object>(); 
                clsItmAdded = true;
                mapCls.put("item_text", closeStr);
                if(DEBUG) Log.i(TAG,"[getListData]map.put:"+closeStr+",curOptSelect:"+curOptSelect);
                if(curOptSelect == 0) {
                    mapCls.put("item_img", R.drawable.item_img_sel);
                }
                else {
                    mapCls.put("item_img", R.drawable.item_img_unsel);
                }
                list.add(mapCls); 
            }

            Map<String, Object> map = new HashMap<String, Object>(); 
            String subTrackStr = trackStr+Integer.toString(i);
            //Log.i(TAG,"[getListData]subTrackStr:"+subTrackStr);
            map.put("item_text", subTrackStr);
            if(DEBUG) Log.i(TAG,"[getListData]map.put["+i+"]:"+subTrackStr+",curOptSelect:"+curOptSelect);

            if(curOptSelect == (i+1)) {
                map.put("item_img", R.drawable.item_img_sel);
            }
            else {
                map.put("item_img", R.drawable.item_img_unsel);
            }
            list.add(map); 
        }

        return list;
    } 

    private void updateListDisplay() {
        Map<String, Object> list_item;
        for (int i = 0; i < lv.getAdapter().getCount(); i++) {						
            list_item = (Map<String, Object>)lv.getAdapter().getItem(i);    						
            if (curOptSelect == i)
                list_item.put("item_img", R.drawable.item_img_sel);
            else
                list_item.put("item_img", R.drawable.item_img_unsel);
        }  
        ((BaseAdapter) lv.getAdapter()).notifyDataSetChanged();  
    }

    private String setSublanguage() {
        String type=null;
        String able=mContext.getResources().getConfiguration().locale.getCountry();
        if(able.equals("TW"))
             type ="BIG5";
        else if(able.equals("JP"))
              type ="cp932";
        else if(able.equals("KR"))
              type ="cp949";
        else if(able.equals("IT")||able.equals("FR")||able.equals("DE"))
              type ="iso88591";
        else if(able.equals("TR"))
              type ="cp1254";
        else
              type ="GBK";
        return type;
    }

    /*public*/private void openFile(SubID filepath) {
        if(DEBUG) Log.i(TAG, "[openFile] filepath: "+filepath);
        if(filepath==null)
            return;
        try {
            if(subTitleView.setFile(filepath,setSublanguage())==Subtitle.SUBTYPE.SUB_INVALID)
            return;
        } 
        catch(Exception e) {
            if(DEBUG) Log.d(TAG, "open:error");
            subTitleView = null;
            e.printStackTrace();
        }
    }

    public void open(String path) {
        if(DEBUG) Log.i(TAG, "[open] path: "+path);
        if(d != null) {
            d.dismiss();
        }
        
        synchronized (this) {
            if(subtitleUtils == null) {
                subtitleUtils = new SubtitleUtils(path);
                curSubId = subtitleUtils.getCurrentInSubtitleIndexByJni();
                if(DEBUG) Log.i(TAG, "[open] curSubId: "+curSubId);
                sendOpenMsg();
                sendInitSelectMsg();
                //option();
            }
        }
    }

    public void close() {
        if(DEBUG) Log.i(TAG, "[close] subTitleView: "+subTitleView);
        if(subtitleUtils != null) {
            subtitleUtils = null;
        }
        subTotal = 0;
        sendCloseMsg();
        //subShowState = SUB_OFF;
    }

    public int getSubTotal() {
        if(DEBUG) Log.i(TAG,"[getSubTotal] subtitleUtils:"+subtitleUtils);
        if(subtitleUtils != null) {
            subTotal = subtitleUtils.getSubTotal();
        }
        if(DEBUG) Log.i(TAG,"[getSubTotal] subTotal:"+subTotal);
        return subTotal;
    }
    
    public void nextSub() { // haven't test
        if(DEBUG) Log.i(TAG,"[nextSub]curSubId:"+curSubId+",subTotal:"+subTotal+",subtitleUtils:"+subtitleUtils);
        if(subtitleUtils != null && subTotal > 0) {
            curSubId++;
            if(curSubId >= subTotal) {
                curSubId = 0;
            }
            sendCloseMsg();
            sendOpenMsg();
        }
    }

    public void preSub() { // haven't test
        if(DEBUG) Log.i(TAG,"[preSub]curSubId:"+curSubId+",subTotal:"+subTotal+",subtitleUtils:"+subtitleUtils);
        if(subtitleUtils != null && subTotal > 0) {
            curSubId--;
            if(curSubId < 0) {
                curSubId = subTotal - 1;
            }
            sendCloseMsg();
            sendOpenMsg();
        }
    } 

    public void openIdx(int idx) {
        if(DEBUG) Log.i(TAG,"[openIdx]idx:"+idx);
        if(subtitleUtils != null && subTotal > 0) {
            curSubId = idx;
            sendCloseMsg();
            sendOpenMsg();
        }
    }

    public void setTextColor(int color) {
        sendSetTxtColorMsg(color);
    }

    public void setTextSize(int size) {
        sendSetTxtSizeMsg(size);
    }

    public void setTextStyle(int style) {
        sendSetTxtStyleMsg(style);
    }

    public void setGravity(int gravity) {
        sendSetGravityMsg(gravity);
    }

    public void setPosHeight(int height) {
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

    public String getCurName() {
        SubID subID = subtitleUtils.getSubID(curSubId);
        if(DEBUG) Log.i(TAG,"[getCurName]subID.filename:"+subID.filename);
        return subID.filename;
    }

    public void startInSubThread(){
        // TODO: 
        if(DEBUG) Log.i(TAG,"[startInSubThread]");
        subThreadState = THREAD_RUN;
    }
    
    public void stopInSubThread(){
        // TODO:
        if(DEBUG) Log.i(TAG,"[stopInSubThread]");
        subThreadState = THREAD_STOP;
    }

    public int getSubThreadState() {
        if(DEBUG) Log.i(TAG,"[getSubThreadState] subThreadState:"+subThreadState);
        return subThreadState;
    }

    public void setSubThreadState(int isRunning) {
        if(DEBUG) Log.i(TAG,"[setSubThreadState] isRunning:"+isRunning);
        subThreadState = isRunning;
    }
    
    public void showSubContent(int position) {
        if(DEBUG) Log.i(TAG,"[showSubContent]position:"+position);
        if (position > 0 && mHandler != null) {
            if (subShowState == SUB_ON ) {
                Message msg = mHandler.obtainMessage(SHOW_CONTENT);
                msg.arg1 = position;
                mHandler.sendMessageDelayed(msg, MSG_SEND_DELAY);
            }
        }
    }

    public void option() {
        if(DEBUG) Log.i(TAG,"[option]");
        if(mHandler != null) {
            Message msg = mHandler.obtainMessage(OPT_SHOW);
            mHandler.sendMessageDelayed(msg, MSG_SEND_DELAY);
        }
    }

    private void sendCloseMsg() {
        if(mHandler != null) {
            Message msg = mHandler.obtainMessage(CLOSE);
            mHandler.sendMessageDelayed(msg, MSG_SEND_DELAY);
            mHandler.removeMessages(SHOW_CONTENT);
            mHandler.removeMessages(OPT_SHOW);
            mHandler.removeMessages(OPEN);
            mHandler.removeMessages(INITSELECT);
        }
    }

    private void sendOpenMsg() {
        if(mHandler != null) {
            Message msg = mHandler.obtainMessage(OPEN);
            mHandler.sendMessageDelayed(msg, MSG_SEND_DELAY);
        }
    }

    private void sendInitSelectMsg() {
         if(mHandler != null) {
            Message msg = mHandler.obtainMessage(INITSELECT);
            mHandler.sendMessageDelayed(msg, MSG_SEND_DELAY);
        }
    }

    private void sendSetTxtColorMsg(int color) {
         if(mHandler != null) {
            Message msg = mHandler.obtainMessage(SET_TXT_COLOR);
            msg.arg1 = color;
            mHandler.sendMessageDelayed(msg, MSG_SEND_DELAY);
        }
    }

    private void sendSetTxtSizeMsg(int size) {
         if(mHandler != null) {
            Message msg = mHandler.obtainMessage(SET_TXT_SIZE);
            msg.arg1 = size;
            mHandler.sendMessageDelayed(msg, MSG_SEND_DELAY);
        }
    }

    private void sendSetTxtStyleMsg(int style) {
         if(mHandler != null) {
            Message msg = mHandler.obtainMessage(SET_TXT_STYLE);
            msg.arg1 = style;
            mHandler.sendMessageDelayed(msg, MSG_SEND_DELAY);
        }
    }

    private void sendSetGravityMsg(int gravity) {
         if(mHandler != null) {
            Message msg = mHandler.obtainMessage(SET_GRAVITY);
            msg.arg1 = gravity;
            mHandler.sendMessageDelayed(msg, MSG_SEND_DELAY);
        }
    }

    private void sendSetPosHeightMsg(int height) {
         if(mHandler != null) {
            Message msg = mHandler.obtainMessage(SET_POS_HEIGHT);
            msg.arg1 = height;
            mHandler.sendMessageDelayed(msg, MSG_SEND_DELAY);
        }
    }

    private void sendHideMsg() {
         if(mHandler != null) {
            Message msg = mHandler.obtainMessage(HIDE);
            mHandler.sendMessageDelayed(msg, MSG_SEND_DELAY);
        }
    }

    private void sendDisplayMsg() {
         if(mHandler != null) {
            Message msg = mHandler.obtainMessage(DISPLAY);
            mHandler.sendMessageDelayed(msg, MSG_SEND_DELAY);
        }
    }

    private void sendClearMsg() {
         if(mHandler != null) {
            Message msg = mHandler.obtainMessage(CLEAR);
            mHandler.sendMessageDelayed(msg, MSG_SEND_DELAY);
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if(DEBUG) Log.i(TAG,"[handleMessage]msg.what:"+msg.what+",subShowState:"+subShowState);
            switch (msg.what) {
                case SHOW_CONTENT:
                    if(subShowState == SUB_ON) {
                        init();
                        int pos = msg.arg1;
                         if (pos > 0) {
                            if(subTitleView != null) {
                                subTitleView.tick(pos);
                            }
                        }
                    }
                    break;
                case OPEN:
                    if(subShowState == SUB_OFF) {
                        subTitleView.startSubThread(); //open insub parse thread
                        subID = subtitleUtils.getSubID(curSubId);
                        if(DEBUG) Log.i(TAG, "[handleMessage] curSubId: "+curSubId+",subID:"+subID);
                        openFile(subID);
                        subShowState = SUB_ON;
                    }
                    break;
                case CLOSE:
                    if(subTitleView != null) {
                        if(DEBUG) Log.i(TAG,"[handleMessage]closeSubtitle");
                        subTitleView.stopSubThread(); //close insub parse thread
                        subTitleView.closeSubtitle();	
                        subTitleView.clear();
                    }
                    subShowState = SUB_OFF;
                    /*if(mWm != null) {
                        mWm.removeView(mSubView);
                    }*/
                    break;
                case INITSELECT:
                    if(getSubTotal() > 0) {
                        if(DEBUG) Log.i(TAG, "[open] subShowState: "+subShowState);
                        if(subShowState == SUB_OFF) {
                            curOptSelect = 0;
                        }
                        else {
                            curOptSelect = curSubId+1; //skip close item, subtitle is open default
                        }
                        if(DEBUG) Log.i(TAG, "[open] curOptSelect: "+curOptSelect);
                    }
                    break;
                case SET_TXT_COLOR:
                    if(subTitleView != null) {
                        int color = msg.arg1;
                        subTitleView.setTextColor(color);
                    }
                    break;
                case SET_TXT_SIZE:
                    if(subTitleView != null) {
                        int size = msg.arg1;
                        subTitleView.setTextSize(size);
                    }
                    break;
                case SET_TXT_STYLE:
                    if(subTitleView != null) {
                        int style = msg.arg1;
                        subTitleView.setTextStyle(style);
                    }
                    break;
                case SET_GRAVITY:
                    if(subTitleView != null) {
                        int gravity = msg.arg1;
                        subTitleView.setGravity(gravity);
                    }
                    break;
                case SET_POS_HEIGHT:
                    if(subTitleView != null) {
                        int height = msg.arg1;
                        subTitleView.setPadding(
                            subTitleView.getPaddingLeft(),
                            subTitleView.getPaddingTop(),
                            subTitleView.getPaddingRight(),height);
                    }
                    break;
                case HIDE:
                    if(subTitleView != null) {
                        //subTitleView.clear();
                        subTitleView.setVisibility(View.GONE);
                        subShowState = SUB_OFF;
                    }
                    break;
                case DISPLAY:
                    if(subTitleView != null) {
                        if(View.VISIBLE != subTitleView.getVisibility()) {
                            subTitleView.setVisibility(View.VISIBLE);
                            subShowState = SUB_ON;
                        }
                    }
                    break;
                case CLEAR:
                    if(subTitleView != null) {
                        subTitleView.clear();
                    }
                    break;

                case OPT_SHOW:
                    showOptionOverlay();
                    break;
            }
        }
    };

    /*static*/ class ServiceStub extends ISubTitleService.Stub {
        WeakReference<SubTitleService> mService;
        
        ServiceStub(SubTitleService service) {
            if(DEBUG) Log.i(TAG,"ServiceStub");
            
            mService = new WeakReference<SubTitleService>(service);
        }

        public void open(String path) {
            mService.get().open(path);
        }

        public void openIdx(int idx) {
            mService.get().openIdx(idx);
        }

        public void close() {
            mService.get().close();
        }

        public int getSubTotal() {
            return mService.get().getSubTotal();
        }

        public void preSub() {
            mService.get().preSub();
        }

        public void nextSub() {
            mService.get().nextSub();
        }

        public void startInSubThread() {
            mService.get().startInSubThread();
        }
    
        public void stopInSubThread() {
            mService.get().stopInSubThread();
        }

        public void showSub(int position) {
            mService.get().showSubContent(position);
        }

        public void option() {
            mService.get().option();
        }

        public void setTextColor(int color) {
            mService.get().setTextColor(color);
        }

        public void setTextSize(int size) {
            mService.get().setTextSize(size);
        }

        public void setGravity(int gravity) {
            mService.get().setGravity(gravity);
        }

        public void setTextStyle(int style) {
            mService.get().setTextStyle(style);
        }

        public void setPosHeight(int height) {
            mService.get().setPosHeight(height);
        }

        public void clear() {
            mService.get().clear();
        }

        public void hide() {
            mService.get().hide();
        }

        public void display() {
            mService.get().display();
        }

        public String getCurName() {
            return mService.get().getCurName();
        }
    }

    private final IBinder mBinder = new ServiceStub(this);

}

