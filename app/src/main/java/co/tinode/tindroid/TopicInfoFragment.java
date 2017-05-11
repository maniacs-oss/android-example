package co.tinode.tindroid;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Array;
import java.util.Collection;

import co.tinode.tindroid.db.StoredSubscription;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Topic Info fragment.
 */
public class TopicInfoFragment extends ListFragment {

    private static final String TAG = "TopicInfoFragment";

    Topic<VCard,String,String> mTopic;
    private MembersAdapter mAdapter;

    public TopicInfoFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_topic_info, container, false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mAdapter = new MembersAdapter(getActivity());
        setListAdapter(mAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();

        Bundle bundle = getArguments();
        String name = bundle.getString("topic");
        mTopic = Cache.getTinode().getTopic(name);


        final Activity activity = getActivity();
        AppCompatImageView avatar = (AppCompatImageView) activity.findViewById(R.id.imageAvatar);
        final TextView title = (TextView) activity.findViewById(R.id.topicTitle);
        final TextView subtitle = (TextView) activity.findViewById(R.id.topicSubtitle);
        final TextView address = (TextView) activity.findViewById(R.id.topicAddress);
        final TextView permissions = (TextView) activity.findViewById(R.id.permissions);

        mTopic.setListener(new Topic.Listener<VCard, String, String>() {
            @Override
            public void onSubsUpdated() {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.resetContent();
                    }
                });
            }

            @Override
            public void onMetaDesc(Description ignored) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        permissions.setText(mTopic.getAccessMode().getMode());
                    }
                });
            }
        });


        VCard pub = mTopic.getPub();
        if (pub != null) {
            if (!TextUtils.isEmpty(pub.fn)) {
                title.setText(pub.fn);
            }
            final Bitmap bmp = pub.getBitmap();
            if (bmp != null) {
                avatar.setImageBitmap(bmp);
            }
        }
        String priv = mTopic.getPriv();
        if (!TextUtils.isEmpty(priv)) {
            subtitle.setText(priv);
        }

        address.setText(mTopic.getName());
        permissions.setText(mTopic.getAccessMode().getMode());

        final Switch muted = (Switch) activity.findViewById(R.id.switchMuted);
        muted.setChecked(mTopic.isMuted());
        muted.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
                Log.d(TAG, "isChecke=" + isChecked + ", muted=" + mTopic.isMuted());
                if (mTopic.isMuted() != isChecked) {
                    try {
                        mTopic.updateMuted(isChecked);
                    } catch (NotConnectedException ignored) {
                        muted.setChecked(!isChecked);
                        Toast.makeText(activity, R.string.error_connection_failed, Toast.LENGTH_SHORT).show();
                    } catch(Exception ignored){
                        muted.setChecked(!isChecked);
                    }
                }
            }
        });

        mAdapter.resetContent();
    }

    @Override
    public void onPause() {
        super.onPause();

        mTopic.setListener(null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstance) {
        super.onActivityCreated(savedInstance);

        // Log.d(TAG, "onActivityCreated");

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                Log.d(TAG, "Click, pos=" + position + ", id=" + id);
            }
        });
    }

    private class MembersAdapter extends BaseAdapter {

        private Subscription<VCard,String>[] mItems;
        private int mItemCount;
        private Context mContext;

        @SuppressWarnings("unchecked")
        MembersAdapter(Activity context) {
            mContext = context;
            mItems = (Subscription<VCard,String>[]) new Subscription[8];
            mItemCount = 0;
        }

        /** Must be run on UI thread */
        void resetContent() {
            if (mTopic != null) {
                Collection<Subscription<VCard, String>> c = mTopic.getSubscriptions();
                if (c != null) {
                    mItemCount = c.size();
                    mItems = c.toArray(mItems);
                } else {
                    mItemCount = 0;
                }
                // Log.d(TAG, "resetContent got " + mItemCount + " items");
                notifyDataSetChanged();
            }
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getCount() {
            return mItemCount;
        }

        @Override
        public Object getItem(int i) {
            return mItems[i];
        }

        @Override
        public long getItemId(int i) {
            return StoredSubscription.getId(mItems[i]);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View item = convertView;
            ViewHolder holder;
            if (item == null) {
                LayoutInflater inflater = (LayoutInflater) mContext
                        .getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

                item = inflater.inflate(R.layout.group_member, parent, false);
                holder = new ViewHolder();
                holder.name = (TextView) item.findViewById(android.R.id.text1);
                holder.contactPriv = (TextView) item.findViewById(android.R.id.text2);
                holder.statusContainer = (LinearLayout) item.findViewById(R.id.statusContainer);
                holder.status = new TextView[holder.statusContainer.getChildCount()];
                for (int i=0; i < holder.status.length; i++) {
                    holder.status[i] = (TextView) holder.statusContainer.getChildAt(i);
                }
                holder.icon = (AppCompatImageView) item.findViewById(android.R.id.icon);

                item.setTag(holder);
            } else {
                holder = (ViewHolder) item.getTag();
            }

            bindView(position, holder);

            return item;
        }

        void bindView(int position, ViewHolder holder) {
            final Subscription<VCard, String> sub = mItems[position];
            final StoredSubscription ss = (StoredSubscription) sub.getLocal();

            Bitmap bmp = null;
            if (sub.pub != null) {
                holder.name.setText(sub.pub.fn);
                bmp = sub.pub.getBitmap();
            } else {
                Log.d(TAG, "Pub is null for " + sub.user);
            }

            holder.contactPriv.setText(sub.priv);

            int i = 0;
            UiUtils.AccessModeLabel[] labels = UiUtils.accessModeLabels(sub.acs, ss.status);
            if (labels != null) {
                for (UiUtils.AccessModeLabel l : labels) {
                    holder.status[i].setText(l.nameId);
                    holder.status[i].setTextColor(l.color);
                    ((GradientDrawable) holder.status[i].getBackground()).setStroke(2, l.color);
                    holder.status[i++].setVisibility(View.VISIBLE);
                }
            }
            for (; i<holder.status.length; i++) {
                holder.status[i].setVisibility(View.GONE);
            }

            UiUtils.assignBitmap(mContext, holder.icon, bmp, R.drawable.ic_person_circle);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_topic_info, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    private static class ViewHolder {
        TextView name;
        TextView contactPriv;
        LinearLayout statusContainer;
        TextView[] status;
        AppCompatImageView icon;
    }
}