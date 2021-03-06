package com.cn.jqj.bleperipheral;

import java.util.ArrayList;
import java.util.List;

import com.cn.jqj.bleperipheral.R;
import com.cn.jqj.bleperipheral.adapter.ChatListAdapter;
import com.cn.jqj.bleperipheral.blelibrary.bluetooth.BLEAdvertiser;
import com.cn.jqj.bleperipheral.blelibrary.bluetooth.BLEServer;
import com.cn.jqj.bleperipheral.blelibrary.bluetooth.IBLECallback;
import com.cn.jqj.bleperipheral.datas.MsgData;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

@SuppressLint("NewApi")
public class MainActivity extends Activity implements IBLECallback {

	public static final String TAG = MainActivity.class.getSimpleName();
	public static final int REQUEST_ENABLE_BLUETOOTH = 15;//请求打开蓝牙
	public static final int SCAN_DURATION = 10000;//扫描时长

	private Button btnStartServer;
	private Button btnSendMsg;
	private Button btnCheckAdvertise;
	private EditText etMsg;
	private ListView listChat;

	private BLEServer bleServer;
	private BLEAdvertiser bleAdvertiser;

	private List<MsgData> msgList;

	private BaseAdapter chatListAdapter;
	private BaseAdapter scanListAdapter;

	private boolean connected;
	private ConnectType connectType;

	private MyHandler mHandler;

	public static final boolean LOG_DEBUG = BuildConfig.DEBUG;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initVariables();
		initViews();
		initData();
	}

	private void initVariables() {
		btnSendMsg = getView(R.id.btn_send);
		btnStartServer = getView(R.id.btn_startServer);
		btnCheckAdvertise = getView(R.id.btn_checkAdvertise);
		etMsg = getView(R.id.et_msg);
		listChat = getView(R.id.list_chat);
		msgList = new ArrayList<>();
		chatListAdapter = new ChatListAdapter(msgList, this);
		connected = false;
		mHandler = new MyHandler();
	}

	private void initViews() {
		listChat.setAdapter(chatListAdapter);

		btnCheckAdvertise.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String msg = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter().isMultipleAdvertisementSupported() ?
						getString(R.string.advertise_support) : getString(R.string.advertise_not_support);
						makeToast(msg);
			}
		});
		btnStartServer.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startServer();
			}
		});
		btnSendMsg.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sendMsg();
			}
		});
	}

	private void initData() {
		bleServer = BLEServer.getInstance(this, this);
		bleAdvertiser = BLEAdvertiser.getInstance(this, new BLEAdvertiser.IAdvertiseResultListener() {
			@Override
			public void onAdvertiseSuccess() {
				if (LOG_DEBUG) {
					Log.i(TAG, "advertise success");
					makeToast("advertise success");
				}
			}

			@Override
			public void onAdvertiseFailed(int errorCode) {
				if (LOG_DEBUG) {
					Log.e(TAG, "advertise failed");
					makeToast("advertise failed");
				}
			}
		});
	}


	private void startServer() {
		bleServer.startGattServer();
		bleAdvertiser.startAdvertise();
		connectType = ConnectType.PERIPHERAL;
	}

	private void startScan() {
		bleAdvertiser.stopAdvertise();
		bleServer.stopGattServer();
		connectType = ConnectType.CENTRAL;
	}

	private void sendMsg() {
		if (!connected) {
			makeToast(MainActivity.this.getString(R.string.not_connected));
			return;
		}
		String msg = etMsg.getText().toString();
		if (connectType == ConnectType.PERIPHERAL) {
			bleServer.sendData(msg.getBytes());
		}
		msgList.add(new MsgData(msg));
		chatListAdapter.notifyDataSetChanged();
		etMsg.setText("");

	}

	@Override
	protected void onStart() {
		super.onStart();
		checkBluetoothOpened();
	}


	@Override
	protected void onResume() {
		super.onResume();
		mHandler.attach(chatListAdapter, scanListAdapter);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mHandler.detach();
	}

	@Override
	public void onConnected() {
		connected = true;
		makeToast(getString(R.string.connected));
	}

	@Override
	public void onDisconnected() {
		connected = false;
		makeToast(getString(R.string.disconnected));
	}

	@Override
	public void onDataReceived(byte[] data) {
		msgList.add(new MsgData(new String(data)));
		mHandler.sendEmptyMessage(MyHandler.REFRESH_CHAT_LIST);
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_ENABLE_BLUETOOTH && resultCode == RESULT_CANCELED) {
			finish();
		}
	}

	private void checkBluetoothOpened() {
		BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
		BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
		if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
		}

	}

	//Handler来刷新UI
	private static class MyHandler extends Handler {
		//可以使用runOnUiThread或者持有View使用View.post来简化代码
		public static final int REFRESH_SCAN_LIST = 250;
		public static final int REFRESH_CHAT_LIST = 38;

		private BaseAdapter chatAdapter;
		private BaseAdapter scanAdapter;

		public void attach(BaseAdapter chatAdapter, BaseAdapter scanAdapter) {
			this.chatAdapter = chatAdapter;
			this.scanAdapter = scanAdapter;
		}

		public void detach() {
			chatAdapter = null;
			scanAdapter = null;
		}

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			if (msg.what == REFRESH_CHAT_LIST) {
				if (chatAdapter != null) {
					chatAdapter.notifyDataSetChanged();
				}
			}
			if (msg.what == REFRESH_SCAN_LIST) {
				if (scanAdapter != null) {
					scanAdapter.notifyDataSetChanged();
				}
			}


		}
	}

	//显示通知
	private void makeToast(final String toast) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(MainActivity.this, toast, Toast.LENGTH_SHORT).show();
			}
		});
	}

	//可以减少许多次强制类型转换
	@SuppressWarnings("unchecked")
	private <T extends View> T getView(int resID) {
		return (T) findViewById(resID);
	}
}
