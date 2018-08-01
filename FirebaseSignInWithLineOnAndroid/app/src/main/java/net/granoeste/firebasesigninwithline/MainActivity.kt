package net.granoeste.firebasesigninwithline

import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.linecorp.linesdk.LineAccessToken
import com.linecorp.linesdk.LineApiResponseCode
import com.linecorp.linesdk.api.LineApiClient
import com.linecorp.linesdk.api.LineApiClientBuilder
import com.linecorp.linesdk.auth.LineLoginApi
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE = 1

    private lateinit var lineApiClient: LineApiClient;

    // Method for preventing orientation changes during ASyncTasks
    private fun lockScreenOrientation() {
        val currentOrientation = resources.configuration.orientation
        if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    // This method is used to reenable orientation changes after an ASyncTask is finished.
    private fun unlockScreenOrientation() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        setContentView(R.layout.activity_main)

        val apiClientBuilder = LineApiClientBuilder(applicationContext, Constants.CHANNEL_ID)
        lineApiClient = apiClientBuilder.build()

        login_button.setOnClickListener {
            try {
                // App to App Login
                val loginIntent = LineLoginApi.getLoginIntent(it.context, Constants.CHANNEL_ID)
                startActivityForResult(loginIntent, REQUEST_CODE)

            } catch (e: Exception) {
                Log.e("ERROR", e.toString())
            }
        }

        browser_login_button.setOnClickListener {
            try {
                // App to App Login
                val loginIntent = LineLoginApi.getLoginIntentWithoutLineAppAuth(it.context, Constants.CHANNEL_ID)
                startActivityForResult(loginIntent, REQUEST_CODE)

            } catch (e: Exception) {
                Log.e("ERROR", e.toString())
            }
        }


        logout_button.setOnClickListener {
            signOut()
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE) {
            Log.e("ERROR", "Unsupported Request")
            return
        }

        val result = LineLoginApi.getLoginResultFromIntent(data)

        when (result.responseCode) {

            LineApiResponseCode.SUCCESS -> {
                Log.d("SUCCESS", "LINE Login Success!!")

                postLineLogin()
            }

            LineApiResponseCode.CANCEL -> Log.e("ERROR", "LINE Login Canceled by user!!")

            else -> {
                Log.e("ERROR", "Login FAILED!")
                Log.e("ERROR", result.errorData.toString())
            }
        }
    }

    private fun postLineLogin() {
        FirebaseApp.initializeApp(this)
        val auth = FirebaseAuth.getInstance()

        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Logging in using LINE.")
        progressDialog.setCancelable(false)
        progressDialog.show()


        getCurrentAccessToken()
                .continueWithTask {
                    val accessToken = it.result.accessToken
                    getFirebaseAuthToken(accessToken)
                }
                .continueWithTask {
                    val firebaseToken = it.result
                    auth.signInWithCustomToken(firebaseToken)
                }
                .addOnCompleteListener {
                    progressDialog.dismiss()

                    if (it.isSuccessful) {
                        Log.d("SUCCESS", "LINE Login was successful.")
                        val user = auth.currentUser
                        Log.d("SUCCESS", "User:" + user.toString())
                    } else {
                        Log.e("ERROR", "LINE Login failed. Error = " + it.exception!!.localizedMessage)
                        AlertDialog.Builder(this@MainActivity)
                                .setMessage("LINE Login failed")
                                .setNegativeButton(android.R.string.no, null)
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .show()
                    }
                }
                .addOnFailureListener {
                    Log.e("ERROR", it.message)
                }
    }

    private fun getCurrentAccessToken(): Task<LineAccessToken> {
        val source = TaskCompletionSource<LineAccessToken>()

        Thread(Runnable {
            val apiResponse = lineApiClient.currentAccessToken

            if (apiResponse.isSuccess) {
                source.setResult(apiResponse.responseData)
            } else {
                source.setException(Exception("Unknown error occurred in LINE SDK."))
            }
        }).start()

        return source.task
    }

    private fun getFirebaseAuthToken(accessToken: String): Task<String> {
        val source = TaskCompletionSource<String>()

        val validationObject = HashMap<String, String>()
        validationObject["accessToken"] = accessToken
        val jsonObject = JSONObject(validationObject)

        val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonObject.toString())
        val request = Request.Builder().url(URL_VEROFY_TOKEN).post(body).build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                source.setException(Exception("Unknown error occurred in LINE SDK."))

            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body()?.string()
                val json = JSONObject(body)
                val firebaseToken = json.optString("firebase_token")
                source.setResult(firebaseToken)
            }
        })

        return source.task
    }

    private fun signOut(): Task<Boolean> {
        val source = TaskCompletionSource<Boolean>()

        FirebaseAuth.getInstance().signOut()
        val apiResponse = lineApiClient.logout()
        if (apiResponse.isSuccess) {
            source.setResult(true)
        } else {
            source.setException(Exception("Unknown error occurred in LINE SDK."))
        }

        return source.task
    }
}
