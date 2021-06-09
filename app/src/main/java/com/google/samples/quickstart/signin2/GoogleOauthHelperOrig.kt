package com.google.samples.quickstart.signin2

import android.content.Context
import androidx.annotation.WorkerThread
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.people.v1.PeopleService

object GoogleOauthHelperOrig {
    @WorkerThread
    fun setUp(context: Context, serverAuthCode: String?): Services {
        val httpTransport: HttpTransport = NetHttpTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        // Redirect URL for web based applications. Can be empty too.
        val redirectUrl = "urn:ietf:wg:oauth:2.0:oob"
        // Exchange auth code for access token
        // Patch for demo.
        val GOOGLE_CLIENT_ID = context.getString(R.string.GOOGLE_CLIENT_ID)
        val GOOGLE_CLIENT_SECRET = context.getString(R.string.GOOGLE_CLIENT_SECRET)
        val tokenResponse = GoogleAuthorizationCodeTokenRequest(
            httpTransport, jsonFactory, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET,
            serverAuthCode, redirectUrl
        )
            .execute()
        // Then, create a GoogleCredential object using the tokens from GoogleTokenResponse
        val credential = GoogleCredential.Builder()
            .setClientSecrets(GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET)
            .setTransport(httpTransport)
            .setJsonFactory(jsonFactory)
            .build()
        credential.setFromTokenResponse(tokenResponse)
        val appPackageName = context.packageName
        val peopleServiceApi = PeopleService.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName(appPackageName)
            .build()
        val peopleService = peopleServiceApi.people()
        val otherContactsService = peopleServiceApi.otherContacts()
        val contactGroups = peopleServiceApi.contactGroups()
        return Services(peopleService, otherContactsService, contactGroups)
    }

    class Services(
        /**https://developers.google.com/people/api/rest/v1/people*/
        val peopleService: PeopleService.People,
        /**https://developers.google.com/people/api/rest/v1/otherContacts*/
        val otherContactsService: PeopleService.OtherContacts,
        /**https://developers.google.com/people/api/rest/v1/contactGroups*/
        val contactGroups: PeopleService.ContactGroups
    )
}
