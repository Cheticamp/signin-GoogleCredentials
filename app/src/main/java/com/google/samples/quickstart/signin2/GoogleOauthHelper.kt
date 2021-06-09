package com.google.samples.quickstart.signin2

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import androidx.annotation.WorkerThread
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.Clock
import com.google.api.services.people.v1.PeopleService
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.UserCredentials
import java.util.*

object GoogleOauthHelper {

    @WorkerThread
    fun setUp(context: Context, serverAuthCode: String?): Services {
        val httpTransport: HttpTransport = NetHttpTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        // Redirect URL for web based applications. Can be empty too.
        val redirectUrl = "urn:ietf:wg:oauth:2.0:oob"

        // Patch for demo
        val GOOGLE_CLIENT_ID = context.getString(R.string.GOOGLE_CLIENT_ID)
        val GOOGLE_CLIENT_SECRET = context.getString(R.string.GOOGLE_CLIENT_SECRET)

        var accessToken: AccessToken? = null
        var refreshToken =
            getDefaultSecuredSharedPreferences(context).getString(
                SecuredSharedPreferences.KEY_GOOGLE_REFRESH_TOKEN,
                null
            )

        if (refreshToken == null) {
            /*  Did we lose the refresh token, or is this the first time? Refresh tokens are only
                returned the first time after the user grants us permission to use the API. So, if
                this is the first time doing this, we should get a refresh token. If it's not the
                first time, we will not get a refresh token, so we will proceed with the access
                token alone. If the access token expires (in about an hour), we will get an error.
                What we should do is to ask the user to reauthorize the app and go through the
                OAuth flow again to recover a refresh token.

                Additional logic is needed to handle reauthorization if the refresh token, itself,
                has become invalid. See https://developers.google.com/identity/protocols/oauth2#expiration
                regarding how a refresh token can become invalid.
            */
            val tokenResponse = GoogleAuthorizationCodeTokenRequest(
                httpTransport, jsonFactory, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET,
                serverAuthCode, redirectUrl
            ).execute()

            refreshToken = tokenResponse.refreshToken
            if (refreshToken != null) {
                getDefaultSecuredSharedPreferences(context).edit()
                    .putString(SecuredSharedPreferences.KEY_GOOGLE_REFRESH_TOKEN, refreshToken)
                    .apply()
            } else {
                Log.d("Applog", "No refresh token. Going with access token alone.")
                val expiresAtMilliseconds =
                    Clock.SYSTEM.currentTimeMillis() + tokenResponse.expiresInSeconds * 1000
                accessToken = AccessToken(tokenResponse.accessToken, Date(expiresAtMilliseconds))
            }
        }

        Log.d("Applog", "Refresh token: $refreshToken")
        // UserCredentials extends GoogleCredentials and permits token refreshing.
        val googleCredentials = UserCredentials.newBuilder().run {
            clientId = GOOGLE_CLIENT_ID
            clientSecret = GOOGLE_CLIENT_SECRET
            setRefreshToken(refreshToken)
            setAccessToken(accessToken)
            build()
        }

        // Save access token on change
        googleCredentials.addChangeListener { oAuth2Credentials ->
            saveAccessToken(oAuth2Credentials.accessToken)
        }

        val requestInitializer: HttpRequestInitializer = HttpCredentialsAdapter(googleCredentials)
        val appPackageName = context.packageName

        val peopleServiceApi = PeopleService.Builder(httpTransport, jsonFactory, requestInitializer)
            .setApplicationName(appPackageName)
            .build()

        return peopleServiceApi.run { Services(people(), otherContacts(), contactGroups()) }
    }

    private fun saveAccessToken(accessToken: AccessToken) {
        // Persist the token securely.
        Log.d("Applog", "Access token has changed: ${accessToken.tokenValue}")
    }

    // Warning insecure!: Patch for demo.
    private fun getDefaultSecuredSharedPreferences(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    // Warning insecure!: Patch for demo.
    object SecuredSharedPreferences {
        const val KEY_GOOGLE_REFRESH_TOKEN = "GOOGLE_REFRESH_TOKEN"
    }

    class Services(
        /**https://developers.google.com/people/api/rest/v1/people*/
        val peopleService: PeopleService.People,
        /**https://developers.google.com/people/api/rest/v1/otherContacts*/
        val otherContactsService: PeopleService.OtherContacts,
        /**https://developers.google.com/people/api/rest/v1/contactGroups*/
        val contactGroups: PeopleService.ContactGroups,
    )
}