package app.olauncher.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import app.olauncher.BuildConfig
import app.olauncher.data.AppModel
import app.olauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt


fun showToastLong(context: Context, message: String) {
    val toast = Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG)
    toast.setGravity(Gravity.CENTER, 0, 0)
    toast.show()
}

fun showToastShort(context: Context, message: String) {
    val toast = Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT)
    toast.setGravity(Gravity.CENTER, 0, 0)
    toast.show()
}

suspend fun getAppsList(context: Context): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()

        try {
            if (!Prefs(context).hiddenAppsUpdated) upgradeHiddenApps(Prefs(context))
            val hiddenApps = Prefs(context).hiddenApps

            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val collator = Collator.getInstance()

            for (profile in userManager.userProfiles) {
                for (app in launcherApps.getActivityList(null, profile)) {
                    if (!hiddenApps.contains(app.applicationInfo.packageName + "|" + profile.toString())
                        and (app.applicationInfo.packageName != BuildConfig.APPLICATION_ID)
                    )
                        appList.add(
                            AppModel(
                                app.label.toString(),
                                collator.getCollationKey(app.label.toString()),
                                app.applicationInfo.packageName,
                                profile
                            )
                        )
                }
            }
            appList.sort()

        } catch (e: java.lang.Exception) {
        }
        appList
    }
}

suspend fun getHiddenAppsList(context: Context): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val pm = context.packageManager
        if (!Prefs(context).hiddenAppsUpdated) upgradeHiddenApps(Prefs(context))

        val hiddenAppsSet = Prefs(context).hiddenApps
        val appList: MutableList<AppModel> = mutableListOf()
        if (hiddenAppsSet.isEmpty()) return@withContext appList

        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val collator = Collator.getInstance()
        for (hiddenPackage in hiddenAppsSet) {
            try {
                val appPackage = hiddenPackage.split("|")[0]
                val userString = hiddenPackage.split("|")[1]
                var userHandle = android.os.Process.myUserHandle()
                for (user in userManager.userProfiles) {
                    if (user.toString() == userString) userHandle = user
                }

                val appInfo = pm.getApplicationInfo(appPackage, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val appKey = collator.getCollationKey(appName)
                appList.add(AppModel(appName, appKey, appPackage, userHandle))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        appList.sort()
        appList
    }
}

// This is to ensure backward compatibility with older app versions
// which did not support multiple user profiles
private fun upgradeHiddenApps(prefs: Prefs) {
    val hiddenAppsSet = prefs.hiddenApps
    val newHiddenAppsSet = mutableSetOf<String>()
    for (hiddenPackage in hiddenAppsSet) {
        if (hiddenPackage.contains("|")) newHiddenAppsSet.add(hiddenPackage)
        else newHiddenAppsSet.add(hiddenPackage + android.os.Process.myUserHandle().toString())
    }
    prefs.hiddenApps = newHiddenAppsSet
    prefs.hiddenAppsUpdated = true
}

fun isPackageInstalled(context: Context, packageName: String, userString: String): Boolean {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val activityInfo = launcher.getActivityList(packageName, getUserHandleFromString(context, userString))
    if (activityInfo.size > 0) return true
    return false
}

fun getUserHandleFromString(context: Context, userHandleString: String): UserHandle {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    for (userHandle in userManager.userProfiles) {
        if (userHandle.toString() == userHandleString) {
            return userHandle
        }
    }
    return android.os.Process.myUserHandle()
}

fun isOlauncherDefault(context: Context?): Boolean {
    val launcherPackageName =
        getDefaultLauncherPackage(context!!)
    return BuildConfig.APPLICATION_ID == launcherPackageName
}

fun getDefaultLauncherPackage(context: Context): String {
    val intent = Intent()
    intent.action = Intent.ACTION_MAIN
    intent.addCategory(Intent.CATEGORY_HOME)
    val packageManager = context.packageManager
    val result = packageManager.resolveActivity(intent, 0)
    return if (result?.activityInfo != null) {
        result.activityInfo.packageName
    } else "android"
}

// Source: https://stackoverflow.com/a/13239706
fun resetDefaultLauncher(context: Context) {
    try {
        val packageManager = context.packageManager
        val componentName = ComponentName(context, FakeHomeActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        val selector = Intent(Intent.ACTION_MAIN)
        selector.addCategory(Intent.CATEGORY_HOME)
        context.startActivity(selector)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun openAppInfo(context: Context, userHandle: UserHandle, packageName: String) {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val intent: Intent? = context.packageManager.getLaunchIntentForPackage(packageName)
    launcher.startAppDetailsActivity(intent?.component, userHandle, null, null)
}

fun openDialerApp(context: Context) {
    try {
        val sendIntent = Intent(Intent.ACTION_DIAL)
        context.startActivity(sendIntent)
    } catch (e: java.lang.Exception) {

    }
}

fun openCameraApp(context: Context) {
    try {
        val sendIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        context.startActivity(sendIntent)
    } catch (e: java.lang.Exception) {

    }
}

fun openAlarmApp(context: Context) {
    try {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        context.startActivity(intent)
    } catch (e: java.lang.Exception) {
        Log.d("TAG", e.toString())
    }
}

fun openCalendar(context: Context) {
    try {
        val cal: Calendar = Calendar.getInstance()
        cal.time = Date()
        val time = cal.time.time
        val builder: Uri.Builder = CalendarContract.CONTENT_URI.buildUpon()
        builder.appendPath("time")
        builder.appendPath(time.toString())
        context.startActivity(Intent(Intent.ACTION_VIEW, builder.build()))
    } catch (e: Exception) {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_APP_CALENDAR)
            context.startActivity(intent)
        } catch (e: Exception) {
        }
    }
}

fun isTablet(context: Context): Boolean {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics = DisplayMetrics()
    windowManager.defaultDisplay.getMetrics(metrics)
    val widthInches = metrics.widthPixels / metrics.xdpi
    val heightInches = metrics.heightPixels / metrics.ydpi
    val diagonalInches = sqrt(widthInches.toDouble().pow(2.0) + heightInches.toDouble().pow(2.0))
    if (diagonalInches >= 7.0) return true
    return false
}

@ColorInt
fun Context.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true
): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}