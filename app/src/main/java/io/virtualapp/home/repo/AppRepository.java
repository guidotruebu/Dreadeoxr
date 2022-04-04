package io.virtualapp.home.repo;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.lody.virtual.GmsSupport;
import com.lody.virtual.client.core.InstallStrategy;
import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.remote.InstallResult;
import com.lody.virtual.remote.InstalledAppInfo;

import org.jdeferred.Promise;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import io.virtualapp.abs.ui.VUiKit;
import io.virtualapp.home.models.AppData;
import io.virtualapp.home.models.AppInfo;
import io.virtualapp.home.models.AppInfoLite;
import io.virtualapp.home.models.MultiplePackageAppData;
import io.virtualapp.home.models.PackageAppData;
import sk.vpkg.manager.RenameAppUtils;
import sk.vpkg.provider.BanNotificationProvider;

/**
 * @author Lody
 */
public class AppRepository implements AppDataSource {

    private static final Collator COLLATOR = Collator.getInstance(Locale.CHINA);
    private static final List<String> SCAN_PATH_LIST = Arrays.asList(
            ".",
            "wandoujia/app",
            "tencent/tassistant/apk",
            "BaiduAsa9103056",
            "360Download",
            "pp/downloader",
            "pp/downloader/apk",
            "pp/downloader/silent/apk");

    private Context mContext;

    public AppRepository(Context context) {
        mContext = context;
    }

    private static boolean isSystemApplication(PackageInfo packageInfo) {
        return (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                && !GmsSupport.isGmsFamilyPackage(packageInfo.packageName);
    }

    @Override
    public Promise<List<AppData>, Throwable, Void> getVirtualApps() {
        return VUiKit.defer().when(() -> {
            try
            {
                List<InstalledAppInfo> infos = VirtualCore.get().getInstalledApps(InstalledAppInfo.FLAG_EXCLUDE_XPOSED_MODULE);
                List<AppData> models = new ArrayList<>();
                if (infos == null) return models;
                for (InstalledAppInfo info : infos)
                {
                    if (!VirtualCore.get().isPackageLaunchable(info.packageName))
                    {
                        continue;
                    }
                    PackageAppData data = new PackageAppData(mContext, info);
                    if (VirtualCore.get().isAppInstalledAsUser(0, info.packageName))
                    {
                        String lpIsSet = RenameAppUtils.getRenamedApp(data.packageName, 0);
                        if (lpIsSet != null)
                        {
                            data.name = lpIsSet;
                            models.add(data);
                        } else
                            models.add(data);
                    }
                    int[] userIds = info.getInstalledUsers();
                    for (int userId : userIds)
                    {
                        if (userId != 0)
                        {
                            data = new PackageAppData(mContext, info);
                            String lpIsSet = RenameAppUtils.getRenamedApp(data.packageName, userId);
                            if (lpIsSet != null)
                            {
                                data.name = lpIsSet;
                                models.add(new MultiplePackageAppData(data, userId));
                            } else
                                models.add(new MultiplePackageAppData(data, userId));
                        }
                    }
                }
                return models;
            }
            catch (Throwable e)
            {
                e.printStackTrace();
                return new ArrayList<>();
            }
        });
    }

    @Override
    public Promise<List<AppData>, Throwable, Void> getVirtualXposedModules() {
        return VUiKit.defer().when(() -> {
            List<AppData> models = new ArrayList<>();
            try
            {
                List<InstalledAppInfo> infos = VirtualCore.get().getInstalledApps(InstalledAppInfo.FLAG_XPOSED_MODULE);
                for (InstalledAppInfo info : infos)
                {
                    PackageAppData data = new PackageAppData(mContext, info);
                    if (VirtualCore.get().isAppInstalledAsUser(0, info.packageName))
                    {
                        models.add(data);
                    }
                    int[] userIds = info.getInstalledUsers();
                    for (int userId : userIds)
                    {
                        if (userId != 0)
                        {
                            models.add(new MultiplePackageAppData(data, userId));
                        }
                    }
                }
            }
            catch (Throwable e)
            {
                e.printStackTrace();
                models = new ArrayList<>();
            }
            return models;
        });
    }

    @Override
    public Promise<List<AppInfo>, Throwable, Void> getInstalledApps(Context context) {
        return VUiKit.defer().when(() -> convertPackageInfoToAppData(context, context.getPackageManager().getInstalledPackages(0), true));
    }

    @Override
    public Promise<List<AppInfo>, Throwable, Void> getInstalledXposedModules(Context context) {
        return VUiKit.defer().when(() -> convertPackageInfoToAppData(context, context.getPackageManager().getInstalledPackages(0), true));
    }

    @Override
    public Promise<List<AppInfo>, Throwable, Void> getStorageApps(Context context, File rootDir) {
        // Now those can scan most application in sdcard.
        // WARNING this method may lead crash on HUAWEI Device.

        /*
        try
        {
            try
            {
                return convertPackageInfoToAppData(context, findAndParseApkRecursively(context, rootDir, null, 0), false);
            } catch (Throwable e)
            {
                e.printStackTrace();
            }
            return convertPackageInfoToAppData(context, findAndParseAPKs(context, rootDir, SCAN_PATH_LIST), false);
        }
        catch (Throwable e)
        {
            e.printStackTrace();
            return new ArrayList<>();
        }

        */
        return VUiKit.defer().when((Callable<List<AppInfo>>) ArrayList::new
        );
    }

    private static final int MAX_SCAN_DEPTH = 3;

    private List<PackageInfo> findAndParseApkRecursively(Context context, File rootDir, List<PackageInfo> result, int depth) {
        if (result == null) {
            result = new ArrayList<>();
        }

        if (depth > MAX_SCAN_DEPTH) {
            return result;
        }

        File[]
                dirFiles = rootDir.listFiles();

        if (dirFiles == null) {
            return Collections.emptyList();
        }

        for (File f: dirFiles) {
            if (f.isDirectory()) {
                try
                {
                    List<PackageInfo> andParseApkRecursively = findAndParseApkRecursively(context, f, new ArrayList<>(), depth + 1);
                    result.addAll(andParseApkRecursively);
                }catch (Throwable e)
                {
                    e.printStackTrace();
                }
            }

            if (!(f.isFile() && f.getName().toLowerCase().endsWith(".apk"))) {
                continue;
            }

            PackageInfo pkgInfo = null;
            try {
                pkgInfo = context.getPackageManager().getPackageArchiveInfo(f.getAbsolutePath(), PackageManager.GET_META_DATA);
                pkgInfo.applicationInfo.sourceDir = f.getAbsolutePath();
                pkgInfo.applicationInfo.publicSourceDir = f.getAbsolutePath();
            } catch (Exception e) {
                // Ignore
            }
            if (pkgInfo != null) {
                result.add(pkgInfo);
            }
        }
        return result;
    }

    private List<PackageInfo> findAndParseAPKs(Context context, File rootDir, List<String> paths) {
        List<PackageInfo> packageList = new ArrayList<>();
        if (paths == null)
            return packageList;
        for (String path : paths) {
            File[] dirFiles = new File(rootDir, path).listFiles();
            if (dirFiles == null)
                continue;
            for (File f : dirFiles) {
                if (!f.getName().toLowerCase().endsWith(".apk"))
                    continue;
                PackageInfo pkgInfo = null;
                try {
                    pkgInfo = context.getPackageManager().getPackageArchiveInfo(f.getAbsolutePath(), 0);
                    pkgInfo.applicationInfo.sourceDir = f.getAbsolutePath();
                    pkgInfo.applicationInfo.publicSourceDir = f.getAbsolutePath();
                } catch (Exception e) {
                    // Ignore
                }
                if (pkgInfo != null)
                    packageList.add(pkgInfo);
            }
        }
        return packageList;
    }

    private List<AppInfo> convertPackageInfoToAppData(Context context, List<PackageInfo> pkgList, boolean fastOpen) {
        PackageManager pm = context.getPackageManager();
        List<AppInfo> list = new ArrayList<>(pkgList.size());
        String hostPkg = VirtualCore.get().getHostPkg();
        for (PackageInfo pkg : pkgList) {
            // ignore the host package
            if (hostPkg.equals(pkg.packageName)) {
                continue;
            }
            // ignore the System package
            if (isSystemApplication(pkg)) {
                continue;
            }
            ApplicationInfo ai = pkg.applicationInfo;
            String path = ai.publicSourceDir != null ? ai.publicSourceDir : ai.sourceDir;
            if (path == null) {
                continue;
            }
            AppInfo info = new AppInfo();
            info.packageName = pkg.packageName;
            info.fastOpen = fastOpen;
            info.path = path;
            info.icon = ai.loadIcon(pm);
            info.name = ai.loadLabel(pm);
            InstalledAppInfo installedAppInfo = null;
            try {
                installedAppInfo = VirtualCore.get().getInstalledAppInfo(pkg.packageName, 0);
            }catch (Throwable exp)
            {
                exp.printStackTrace();
            }
            if (installedAppInfo != null) {
                info.cloneCount = installedAppInfo.getInstalledUsers().length;
            }
            list.add(info);
        }
        return list;
    }

    @Override
    public InstallResult addVirtualApp(AppInfoLite info) {
        int flags = InstallStrategy.COMPARE_VERSION | InstallStrategy.SKIP_DEX_OPT;
        if (info.fastOpen) {
            flags |= InstallStrategy.DEPEND_SYSTEM_IF_EXIST;
        }
        return VirtualCore.get().installPackage(info.path, flags);
    }

    @Override
    public boolean removeVirtualApp(String packageName, int userId) {
        return VirtualCore.get().uninstallPackageAsUser(packageName, userId);
    }

}
