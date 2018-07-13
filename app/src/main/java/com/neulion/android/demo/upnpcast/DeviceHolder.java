package com.neulion.android.demo.upnpcast;

import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import com.neulion.android.demo.upnpcast.DeviceAdapter.OnItemSelectedListener;
import com.neulion.android.upnpcast.device.CastDevice;

/**
 * User: liuwei(wei.liu@neulion.com.com)
 * Date: 2018-07-02
 * Time: 16:32
 */
public class DeviceHolder extends ViewHolder implements OnClickListener, OnCheckedChangeListener
{
    private TextView name;
    private TextView description;
    private CheckBox selector;
    private OnItemSelectedListener mOnItemSelectedListener;
    private CastDevice mCastDevice;
    private boolean mBinding = false;

    DeviceHolder(final View itemView, OnItemSelectedListener listener)
    {
        super(itemView);

        mOnItemSelectedListener = listener;

        itemView.setOnClickListener(this);
        name = itemView.findViewById(R.id.device_name);
        description = itemView.findViewById(R.id.device_description);
        selector = itemView.findViewById(R.id.device_selector);
        selector.setOnCheckedChangeListener(this);
    }

    public void setData(CastDevice castDevice, boolean isSelected)
    {
        mBinding = true;
        mCastDevice = castDevice;
        name.setText(castDevice.getName());
        description.setText(castDevice.getDescription());
        selector.setChecked(isSelected);
        mBinding = false;
    }

    @Override
    public void onClick(View v)
    {
        selector.setChecked(!selector.isChecked());
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
        if (mOnItemSelectedListener != null && !mBinding)
        {
            mOnItemSelectedListener.onItemSelected(mCastDevice, isChecked);
        }
    }
}
