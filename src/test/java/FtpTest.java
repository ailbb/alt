import com.ailbb.ajj.entity.$ConnConfiguration;
import com.ailbb.ajj.entity.$Result;
import com.ailbb.alt.$;
import com.ailbb.alt.ftp.$Ftp;
import org.apache.commons.net.ftp.FTPFile;

/**
 * Created by Wz on 8/13/2019.
 */
public class FtpTest {
    public static void main(String[] args) {
        $Ftp ftp = $.ftp.setConnConfiguration(
                new $ConnConfiguration()
                        .setIp("132.225.129.72")
                        .setPort(21)
                        .setUsername("username")
                        .setPassword("passwd")
        );

        $Result rs = ftp.login(); // 登录
        if(!rs.isSuccess()) return;

        try {
            FTPFile[] fs = ( FTPFile[] )ftp.listFile("/xdata0/data/WebServerData/ActiveUserDay").getData();
            System.out.println(fs);
        } catch (Exception e) {
            ftp.logout();
        }

    }
}
