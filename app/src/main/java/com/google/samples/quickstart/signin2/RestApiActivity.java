package com.google.samples.quickstart.signin2;

/*
    This file has been heavily modified from the original version which was published as part of the
    Google signin quickstart.

    See https://github.com/googlesamples/google-services/tree/master/android/signin
 */

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.services.people.v1.PeopleServiceScopes;
import com.google.api.services.people.v1.model.ListConnectionsResponse;
import com.google.api.services.people.v1.model.Person;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Activity to demonstrate using the Google Sign In API with a Google API that uses the Google
 * Java Client Library rather than a Google Play services API. See {@link #getContacts()}
 * for how to access the People API using this method.
 * <p>
 * In order to use this Activity you must enable the People API on your project. Visit the following
 * link and replace 'YOUR_PROJECT_ID' to enable the API:
 * https://console.developers.google.com/apis/api/people.googleapis.com/overview?project=YOUR_PROJECT_ID
 */
public class RestApiActivity extends AppCompatActivity implements
        View.OnClickListener {

    private static final String TAG = "RestApiActivity";

    // Scope for reading user's contacts
    private static final String CONTACTS_SCOPE = "https://www.googleapis.com/auth/contacts.readonly";

    // Bundle key for account object
    private static final String KEY_ACCOUNT = "key_account";

    // Request codes
    private static final int RC_SIGN_IN = 9001;
    private static final int RC_RECOVERABLE = 9002;

    private GoogleSignInClient mGoogleSignInClient;

    private Account mAccount;
    public String mServerAuthCode;

    private TextView mStatusTextView;
    private TextView mDetailTextView;
    private ProgressDialog mProgressDialog;

    public GoogleOauthHelper.Services mServices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Views
        mStatusTextView = findViewById(R.id.status);
        mDetailTextView = findViewById(R.id.detail);

        // Button listeners
        findViewById(R.id.sign_in_button).setOnClickListener(this);
        findViewById(R.id.sign_out_button).setOnClickListener(this);
        findViewById(R.id.refetch_button).setOnClickListener(this);

        // For this example we don't need the disconnect button
        findViewById(R.id.disconnect_button).setVisibility(View.GONE);

        // Restore instance state
        if (savedInstanceState != null) {
            mAccount = savedInstanceState.getParcelable(KEY_ACCOUNT);
        }

        // Configure sign-in to request the user's ID, email address, basic profile,
        // and readonly access to contacts.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(
                        new Scope(PeopleServiceScopes.CONTACTS_READONLY),
                        new Scope(PeopleServiceScopes.USERINFO_PROFILE),
                        new Scope(PeopleServiceScopes.USER_EMAILS_READ),
                        new Scope(PeopleServiceScopes.CONTACTS),
                        new Scope(PeopleServiceScopes.CONTACTS_OTHER_READONLY))
                .requestServerAuthCode(getString(R.string.server_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

//        mGoogleSignInClient.revokeAccess();

        // Show a standard Google Sign In button. If your application does not rely on Google Sign
        // In for authentication you could replace this with a "Get Google Contacts" button
        // or similar.
        SignInButton signInButton = findViewById(R.id.sign_in_button);
        signInButton.setSize(SignInButton.SIZE_STANDARD);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Check if the user is already signed in and all required scopes are granted
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (GoogleSignIn.hasPermissions(account, new Scope(CONTACTS_SCOPE))) {
            updateUI(account);
        } else {
            updateUI(null);
        }
    }

    @Override
    protected void onSaveInstanceState(@NotNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_ACCOUNT, mAccount);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }

        // Handling a user-recoverable auth exception
        if (requestCode == RC_RECOVERABLE) {
            if (resultCode == RESULT_OK) {
                getContacts();
            } else {
                Toast.makeText(this, R.string.msg_contacts_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void signOut() {
        // Signing out clears the current authentication state and resets the default user,
        // this should be used to "switch users" without fully un-linking the user's google
        // account from your application.
        mGoogleSignInClient.signOut().addOnCompleteListener(this, new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                updateUI(null);
            }
        });
    }

    private void handleSignInResult(@NonNull Task<GoogleSignInAccount> completedTask) {
        Log.d(TAG, "handleSignInResult:" + completedTask.isSuccessful());

        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            updateUI(account);
            mServerAuthCode = account.getServerAuthCode();

            // Store the account from the result
            mAccount = account.getAccount();
            findViewById(R.id.refetch_button).setVisibility(View.VISIBLE);

            // Asynchronously access the People API for the account
            getContacts();
        } catch (ApiException e) {
            Log.w(TAG, "handleSignInResult:error", e);

            // Clear the local account
            mAccount = null;

            // Signed out, show unauthenticated UI.
            updateUI(null);
        }
    }

    private void getContacts() {
        if (mAccount == null) {
            Log.w(TAG, "getContacts: null account");
            return;
        }

        if (mServerAuthCode == null) {
            Log.w(TAG, "getContacts: null server auth code");
            return;
        }

        showProgressDialog();
        new GetContactsTask(this).execute(mAccount);
    }

    protected void onConnectionsLoadFinished(@Nullable List<Person> connections) {
        hideProgressDialog();

        if (connections == null) {
            Log.d(TAG, "getContacts:connections: null");
            mDetailTextView.setText(getString(R.string.connections_fmt, "None"));
            return;
        }

        Log.d(TAG, "getContacts:connections: size=" + connections.size());

        // Get names of all connections
        StringBuilder msg = new StringBuilder();
        for (int i = 0; i < connections.size(); i++) {
            Person person = connections.get(i);
            if (person.getNames() != null && person.getNames().size() > 0) {
                msg.append(person.getNames().get(0).getDisplayName());

                if (i < connections.size() - 1) {
                    msg.append(",");
                }
            }
        }

        // Display names
        mDetailTextView.setText(getString(R.string.connections_fmt, msg.toString()));
    }

    protected void onRecoverableAuthException(UserRecoverableAuthIOException recoverableException) {
        Log.w(TAG, "onRecoverableAuthException", recoverableException);
        startActivityForResult(recoverableException.getIntent(), RC_RECOVERABLE);
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.sign_in_button:
                signIn();
                break;
            case R.id.sign_out_button:
                signOut();
                break;
            case R.id.refetch_button:
                mDetailTextView.setText("Refetching contacts...");
                getContacts();
                break;
        }
    }

    private void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setMessage(getString(R.string.loading));
            mProgressDialog.setIndeterminate(true);
        }

        mProgressDialog.show();
    }

    private void hideProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.hide();
        }
    }

    private void updateUI(@Nullable GoogleSignInAccount account) {
        if (account != null) {
            mStatusTextView.setText(getString(R.string.signed_in_fmt, account.getDisplayName()));

            findViewById(R.id.sign_in_button).setVisibility(View.GONE);
            findViewById(R.id.sign_out_and_disconnect).setVisibility(View.VISIBLE);
        } else {
            mStatusTextView.setText(R.string.signed_out);
            mDetailTextView.setText(null);

            findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            findViewById(R.id.sign_out_and_disconnect).setVisibility(View.GONE);
        }
    }

    private static class GetContactsTask extends AsyncTask<Account, Void, List<Person>> {

        private final WeakReference<RestApiActivity> mActivityRef;

        public GetContactsTask(RestApiActivity activity) {
            mActivityRef = new WeakReference<>(activity);
        }

        @Override
        protected List<Person> doInBackground(Account... accounts) {
            if (mActivityRef.get() == null) {
                return null;
            }

            String mServerAuthCode = mActivityRef.get().mServerAuthCode;
            if (TextUtils.isEmpty(mServerAuthCode)) {
                return null;
            }

            Context context = mActivityRef.get().getApplicationContext();
            try {

                GoogleOauthHelper.Services services;
                if (mActivityRef.get().mServices == null) {
                    Log.d("Applog", "Getting new services");
                    services = GoogleOauthHelper.INSTANCE.setUp(context, mServerAuthCode);
                    mActivityRef.get().mServices = services;
                } else {
                    Log.d("Applog", "Using old services");
                    services = mActivityRef.get().mServices;
                }
                ListConnectionsResponse connectionsResponse = services
                        .getPeopleService()
                        .connections()
                        .list("people/me")
                        .setRequestMaskIncludeField("person.names,person.phoneNumbers,person.emailAddresses")
                        .execute();

                return connectionsResponse.getConnections();

            } catch (UserRecoverableAuthIOException recoverableException) {
                if (mActivityRef.get() != null) {
                    mActivityRef.get().onRecoverableAuthException(recoverableException);
                }
            } catch (IOException e) {
                Log.w(TAG, "getContacts:exception", e);
            }

            return null;
        }

        @Override
        protected void onPostExecute(List<Person> people) {
            super.onPostExecute(people);
            if (mActivityRef.get() != null) {
                mActivityRef.get().onConnectionsLoadFinished(people);
            }
        }
    }
}
