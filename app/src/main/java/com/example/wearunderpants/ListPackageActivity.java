package com.example.wearunderpants;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ListPackageActivity extends AppCompatActivity {

    private ListView listView;
    private EditText searchEt;
    MyBaseAdapter myBaseAdapter;
    List<AppInfo> mData = new ArrayList<AppInfo>();
    List<AppInfo> mDataBack = new ArrayList<>();
    private final String PKGLIST_TAG = "packageList";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_package2);
        mData = new ArrayList<AppInfo>();
        //创建一个线程
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    initPackList();
                }  catch  (Exception e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        myBaseAdapter  = new MyBaseAdapter(getApplication(), mData);
                        listView.setAdapter(myBaseAdapter);
                        myBaseAdapter.notifyDataSetChanged();
                        Toast.makeText(getApplication(), "加载完成" , Toast.LENGTH_SHORT).show();
                    }
                });

            }
        }).start();

        initView();
    }

    class AppInfo implements Serializable {
        String appName;
        String packageName;
        Drawable icon;
        boolean isSelect;

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public void setIcon(Drawable icon) {
            this.icon = icon;
        }

        public void setSelect(boolean select) {
            isSelect = select;
        }
    }

    private void initPackList() {

        SharedPreferences preferences = getSharedPreferences("serverInfo",
                Activity.MODE_PRIVATE);
        List<String> selectList = new ArrayList<>();
        Log.d("当前包", preferences.getString("packageList", ""));
        if (!preferences.getString("packageList", "").equals("")) {
            try {
                JSONArray jObject1 = new JSONArray(preferences.getString("packageList", ""));
                for (int i = 0; i<jObject1.length(); i++) {
                    selectList.add(jObject1.getString(i));
                }
            } catch (Exception e) {
                Log.e("json parse error2", "json parse error2");
            }
        }
        final List<PackageInfo> packages = getPackageManager().getInstalledPackages(0);
        for (PackageInfo packageInfo : packages) {
            if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) { //非系统应用
                // AppInfo 自定义类，包含应用信息
                AppInfo appInfo = new AppInfo();
                appInfo.setAppName(packageInfo.applicationInfo.loadLabel(getPackageManager()).toString());//获取应用名称
                appInfo.setPackageName(packageInfo.packageName); //获取应用包名，可用于卸载和启动应用
                appInfo.setIcon(packageInfo.applicationInfo.loadIcon(getPackageManager()));//获取应用图标
                appInfo.setSelect(false);
                if (selectList.contains(packageInfo.packageName)) {
                    appInfo.setSelect(true);
                }
                mData.add(appInfo);
                mDataBack.add(appInfo);
            } else { // 系统应用

            }
        }
    }

    private void search(String keyword) {
//        Toast.makeText(this, keyword, Toast.LENGTH_LONG).show();
        mData.clear();
        for (AppInfo info: mDataBack) {
//            Log.d("package", info.appName);
            if (info.appName.contains(keyword) || info.packageName.contains(keyword)) {
                mData.add(info);
            }
        }
        myBaseAdapter.notifyDataSetChanged();
    }

    private void initView() {
        listView = findViewById(R.id.ListView);
        searchEt = findViewById(R.id.searchEt);
        searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                search(charSequence.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Toast.makeText(getApplication(), mData.get(i).appName, Toast.LENGTH_LONG).show();
                System.out.println("modify app data"+mData.get(i).appName);
                AppInfo app = mData.get(i);
                if (app.isSelect) {
                    app.setSelect(false);
                    mData.get(i).setSelect(false);
                } else {
                    app.setSelect(true);
                    mData.get(i).setSelect(true);
                }
                myBaseAdapter.notifyDataSetChanged();
                savePkgList();
            }
        });

    }

    public class MyBaseAdapter extends BaseAdapter {
        private LayoutInflater layoutInflater;//得到一个LayoutInfalter对象用来导入布局
        private List<AppInfo> list = new ArrayList<>();//得到一个List<App>集合用来导入数据

        //构造函数
        public MyBaseAdapter(Context context, List<AppInfo> list) {
            this.layoutInflater =LayoutInflater.from(context);
            this.list = list;
        }

        @Override
        //return 多少就有个多少个item列表
        public int getCount() {//返回ListView Item条目的总数
            return list.size();
        }

        @Override
        public Object getItem(int position) {//返回ListView Item条目代表的对象
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {//返回ListView Item的id
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;
            if (convertView == null) {
                //绑定Item布局
                convertView = layoutInflater.inflate(R.layout.list_item, null, false);
                //自定义内部类，对象holder用来存储文字和图片控件
                holder = new ViewHolder();
                final CheckBox ckbItem = (CheckBox) convertView.findViewById(R.id.select_ckb);
                ckbItem.setEnabled(false);
                holder.mTextView = (TextView) convertView.findViewById(R.id.item_tv);
                holder.imageView = (ImageView) convertView.findViewById(R.id.item_image);
                holder.mCheckBox = ckbItem;

//                ckbItem.setOnClickListener(new View.OnClickListener() {
//                    @Override
//                    public void onClick(View view) {
//
//                    }
//                });
                //将holder放入当前视图中
                convertView.setTag(holder);
            } else {
                //复用holder
                holder = (ViewHolder) convertView.getTag();
            }
            //取出app对象
            AppInfo app=list.get(position);//此处%7就可以无限轮播
            holder.mTextView.setText(app.appName);
            holder.imageView.setImageDrawable(app.icon);
            holder.mCheckBox.setChecked(app.isSelect);
            return convertView;
        }
        //内部类
        class ViewHolder {
            CheckBox mCheckBox;
            TextView mTextView;
            ImageView imageView;
        }
    }

    private void savePkgList() {
        List<String> data = new ArrayList<>();
        for (AppInfo info: mData) {
            if (info.isSelect) {
                data.add(info.packageName);
            }
        }
        JSONArray jsonArray = new JSONArray(data);
        SharedPreferences preferences = getSharedPreferences("serverInfo",
                Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();//获取编辑器
        editor.putString(PKGLIST_TAG, jsonArray.toString());
        editor.apply();
        Toast.makeText(getApplicationContext(), "save!", Toast.LENGTH_LONG).show();
    }

    private boolean checkIsFirstInstall() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getApplication().getPackageName(), 0);
            return info.firstInstallTime == info.lastUpdateTime;
        } catch (Exception e) {
            return true;
        }
    }
}