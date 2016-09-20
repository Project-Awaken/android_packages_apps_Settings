/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import android.annotation.LayoutRes;
import android.annotation.Nullable;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.security.Credentials;
import android.security.IKeyChainService;
import android.security.KeyChain;
import android.security.KeyChain.KeyChainConnection;
import android.security.KeyStore;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class UserCredentialsSettings extends OptionsMenuFragment implements OnItemClickListener {
    private static final String TAG = "UserCredentialsSettings";

    private ListView mListView;

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.USER_CREDENTIALS;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshItems();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.user_credentials, parent, false);

        // Set up an OnItemClickListener for the credential list.
        mListView = (ListView) rootView.findViewById(R.id.credential_list);
        mListView.setOnItemClickListener(this);

        return rootView;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final Credential item = (Credential) parent.getItemAtPosition(position);
        CredentialDialogFragment.show(this, item);
    }

    protected void announceRemoval(String alias) {
        if (isAdded()) {
            mListView.announceForAccessibility(getString(R.string.user_credential_removed, alias));
        }
    }

    protected void refreshItems() {
        if (isAdded()) {
            new AliasLoader().execute();
        }
    }

    public static class CredentialDialogFragment extends InstrumentedDialogFragment {
        private static final String TAG = "CredentialDialogFragment";
        private static final String ARG_CREDENTIAL = "credential";

        public static void show(Fragment target, Credential item) {
            final Bundle args = new Bundle();
            args.putParcelable(ARG_CREDENTIAL, item);

            if (target.getFragmentManager().findFragmentByTag(TAG) == null) {
                final DialogFragment frag = new CredentialDialogFragment();
                frag.setTargetFragment(target, /* requestCode */ -1);
                frag.setArguments(args);
                frag.show(target.getFragmentManager(), TAG);
            }
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Credential item = (Credential) getArguments().getParcelable(ARG_CREDENTIAL);

            View root = getActivity().getLayoutInflater()
                    .inflate(R.layout.user_credential_dialog, null);
            ViewGroup infoContainer = (ViewGroup) root.findViewById(R.id.credential_container);
            View contentView = getCredentialView(item, R.layout.user_credential, null,
                    infoContainer, /* expanded */ true);
            infoContainer.addView(contentView);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setView(root)
                    .setTitle(R.string.user_credential_title)
                    .setPositiveButton(R.string.done, null);

            final String restriction = UserManager.DISALLOW_CONFIG_CREDENTIALS;
            final int myUserId = UserHandle.myUserId();
            if (!RestrictedLockUtils.hasBaseUserRestriction(getContext(), restriction, myUserId)) {
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int id) {
                        final EnforcedAdmin admin = RestrictedLockUtils.checkIfRestrictionEnforced(
                                getContext(), restriction, myUserId);
                        if (admin != null) {
                            RestrictedLockUtils.sendShowAdminSupportDetailsIntent(getContext(),
                                    admin);
                        } else {
                            new RemoveCredentialsTask(getContext(), getTargetFragment())
                                    .execute(item);
                        }
                        dialog.dismiss();
                    }
                };
                if (item.isSystem()) {
                    // TODO: a safe means of clearing wifi certificates. Configs refer to aliases
                    //       directly so deleting certs will break dependent access points.
                    builder.setNegativeButton(R.string.trusted_credentials_remove_label, listener);
                }
            }
            return builder.create();
        }

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.DIALOG_USER_CREDENTIAL;
        }

        /**
         * Deletes all certificates and keys under a given alias.
         *
         * If the {@link Credential} is for a system alias, all active grants to the alias will be
         * removed using {@link KeyChain}.
         */
        private class RemoveCredentialsTask extends AsyncTask<Credential, Void, Credential[]> {
            private Context context;
            private Fragment targetFragment;

            public RemoveCredentialsTask(Context context, Fragment targetFragment) {
                this.context = context;
                this.targetFragment = targetFragment;
            }

            @Override
            protected Credential[] doInBackground(Credential... credentials) {
                for (final Credential credential : credentials) {
                    if (credential.isSystem()) {
                        removeGrantsAndDelete(credential);
                        continue;
                    }
                    throw new UnsupportedOperationException(
                            "Not implemented for wifi certificates. This should not be reachable.");
                }
                return credentials;
            }

            private void removeGrantsAndDelete(final Credential credential) {
                final KeyChainConnection conn;
                try {
                    conn = KeyChain.bind(getContext());
                } catch (InterruptedException e) {
                    Log.w(TAG, "Connecting to KeyChain", e);
                    return;
                }

                try {
                    IKeyChainService keyChain = conn.getService();
                    keyChain.removeKeyPair(credential.alias);
                } catch (RemoteException e) {
                    Log.w(TAG, "Removing credentials", e);
                } finally {
                    conn.close();
                }
            }

            @Override
            protected void onPostExecute(Credential... credentials) {
                if (targetFragment instanceof UserCredentialsSettings && targetFragment.isAdded()) {
                    final UserCredentialsSettings target = (UserCredentialsSettings) targetFragment;
                    for (final Credential credential : credentials) {
                        target.announceRemoval(credential.alias);
                    }
                    target.refreshItems();
                }
            }
        }
    }

    /**
     * Opens a background connection to KeyStore to list user credentials.
     * The credentials are stored in a {@link CredentialAdapter} attached to the main
     * {@link ListView} in the fragment.
     */
    private class AliasLoader extends AsyncTask<Void, Void, List<Credential>> {
        /**
         * @return a list of credentials ordered:
         * <ol>
         *   <li>first by purpose;</li>
         *   <li>then by alias.</li>
         * </ol>
         */
        @Override
        protected List<Credential> doInBackground(Void... params) {
            final KeyStore keyStore = KeyStore.getInstance();

            // Certificates can be installed into SYSTEM_UID or WIFI_UID through CertInstaller.
            final int myUserId = UserHandle.myUserId();
            final int systemUid = UserHandle.getUid(myUserId, Process.SYSTEM_UID);
            final int wifiUid = UserHandle.getUid(myUserId, Process.WIFI_UID);

            List<Credential> credentials = new ArrayList<>();
            credentials.addAll(getCredentialsForUid(keyStore, systemUid).values());
            credentials.addAll(getCredentialsForUid(keyStore, wifiUid).values());
            return credentials;
        }

        private SortedMap<String, Credential> getCredentialsForUid(KeyStore keyStore, int uid) {
            final SortedMap<String, Credential> aliasMap = new TreeMap<>();
            for (final Credential.Type type : Credential.Type.values()) {
                for (final String alias : keyStore.list(type.prefix, uid)) {
                    // Do not show work profile keys in user credentials
                    if (alias.startsWith(LockPatternUtils.PROFILE_KEY_NAME_ENCRYPT) ||
                            alias.startsWith(LockPatternUtils.PROFILE_KEY_NAME_DECRYPT)) {
                        continue;
                    }
                    Credential c = aliasMap.get(alias);
                    if (c == null) {
                        c = new Credential(alias, uid);
                        aliasMap.put(alias, c);
                    }
                    c.storedTypes.add(type);
                }
            }
            return aliasMap;
        }

        @Override
        protected void onPostExecute(List<Credential> credentials) {
            final Credential[] credentialArray = credentials.toArray(new Credential[0]);
            mListView.setAdapter(new CredentialAdapter(getContext(), credentialArray));
        }
    }

    /**
     * Helper class to display {@link Credential}s in a list.
     */
    private static class CredentialAdapter extends ArrayAdapter<Credential> {
        private static final int LAYOUT_RESOURCE = R.layout.user_credential_preference;

        public CredentialAdapter(Context context, final Credential[] objects) {
            super(context, LAYOUT_RESOURCE, objects);
        }

        @Override
        public View getView(int position, @Nullable View view, ViewGroup parent) {
            return getCredentialView(getItem(position), LAYOUT_RESOURCE, view, parent,
                    /* expanded */ false);
        }
    }

    /**
     * Mapping from View IDs in {@link R} to the types of credentials they describe.
     */
    private static final SparseArray<Credential.Type> credentialViewTypes = new SparseArray<>();
    static {
        credentialViewTypes.put(R.id.contents_userkey, Credential.Type.USER_PRIVATE_KEY);
        credentialViewTypes.put(R.id.contents_usercrt, Credential.Type.USER_CERTIFICATE);
        credentialViewTypes.put(R.id.contents_cacrt, Credential.Type.CA_CERTIFICATE);
    }

    protected static View getCredentialView(Credential item, @LayoutRes int layoutResource,
            @Nullable View view, ViewGroup parent, boolean expanded) {
        if (view == null) {
            view = LayoutInflater.from(parent.getContext()).inflate(layoutResource, parent, false);
        }

        ((TextView) view.findViewById(R.id.alias)).setText(item.alias);
        ((TextView) view.findViewById(R.id.purpose)).setText(item.isSystem()
                ? R.string.credential_for_vpn_and_apps
                : R.string.credential_for_wifi);

        view.findViewById(R.id.contents).setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (expanded) {
            for (int i = 0; i < credentialViewTypes.size(); i++) {
                final View detail = view.findViewById(credentialViewTypes.keyAt(i));
                detail.setVisibility(item.storedTypes.contains(credentialViewTypes.valueAt(i))
                        ? View.VISIBLE : View.GONE);
            }
        }
        return view;
    }

    static class AliasEntry {
        public String alias;
        public int uid;
    }

    static class Credential implements Parcelable {
        static enum Type {
            CA_CERTIFICATE (Credentials.CA_CERTIFICATE),
            USER_CERTIFICATE (Credentials.USER_CERTIFICATE),
            USER_PRIVATE_KEY (Credentials.USER_PRIVATE_KEY),
            USER_SECRET_KEY (Credentials.USER_SECRET_KEY);

            final String prefix;

            Type(String prefix) {
                this.prefix = prefix;
            }
        }

        /**
         * Main part of the credential's alias. To fetch an item from KeyStore, prepend one of the
         * prefixes from {@link CredentialItem.storedTypes}.
         */
        final String alias;

        /**
         * UID under which this credential is stored. Typically {@link Process#SYSTEM_UID} but can
         * also be {@link Process#WIFI_UID} for credentials installed as wifi certificates.
         */
        final int uid;

        /**
         * Should contain some non-empty subset of:
         * <ul>
         *   <li>{@link Credentials.CA_CERTIFICATE}</li>
         *   <li>{@link Credentials.USER_CERTIFICATE}</li>
         *   <li>{@link Credentials.USER_PRIVATE_KEY}</li>
         *   <li>{@link Credentials.USER_SECRET_KEY}</li>
         * </ul>
         */
        final EnumSet<Type> storedTypes = EnumSet.noneOf(Type.class);

        Credential(final String alias, final int uid) {
            this.alias = alias;
            this.uid = uid;
        }

        Credential(Parcel in) {
            this(in.readString(), in.readInt());

            long typeBits = in.readLong();
            for (Type i : Type.values()) {
                if ((typeBits & (1L << i.ordinal())) != 0L) {
                    storedTypes.add(i);
                }
            }
        }

        public void writeToParcel(Parcel out, int flags) {
            out.writeString(alias);
            out.writeInt(uid);

            long typeBits = 0;
            for (Type i : storedTypes) {
                typeBits |= 1L << i.ordinal();
            }
            out.writeLong(typeBits);
        }

        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<Credential> CREATOR
                = new Parcelable.Creator<Credential>() {
            public Credential createFromParcel(Parcel in) {
                return new Credential(in);
            }

            public Credential[] newArray(int size) {
                return new Credential[size];
            }
        };

        public boolean isSystem() {
            return UserHandle.getAppId(uid) == Process.SYSTEM_UID;
        }
    }
}
