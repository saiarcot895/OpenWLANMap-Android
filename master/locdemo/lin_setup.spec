Summary: LocDemo libwlocate demo application
Name: LocDemo
Version: 1.2
Release: 1
License: GPL
Packager: http://www.openwlanmap.org
Group: Applications/Internet
Requires: wxGTK, wireless-tools
%description
A WLAN geolocation demonstration application that makes use of libwlocate and the data of project OpenWLANMap. It is recommended to execute this application with root privileges to get more detailed WLAN information and to get a more exact position information.
%files
%{_bindir}/LocDemo
%{_bindir}/wlocd
%{_bindir}/lwtrace
%{_datadir}/icons/LocDemo-icon.png
%{_datadir}/applications/fedora-locdemo.desktop
%{_libdir}/libwlocate.so
%{_initrddir}/wlocd

%pre
if [ -x /sbin/LocDemo ]; then
   rm -f /sbin/LocDemo
fi
if [ -x /etc/init.d/wlocd ]; then
   /etc/init.d/wlocd stop
fi
%preun
/etc/init.d/wlocd stop
rm -rf /etc/rc5.d/S99wlocd 1>/dev/null 2>/dev/null
rm -rf /etc/rc5.d/K01wlocd 1>/dev/null 2>/dev/null
rm -rf /etc/rc3.d/S99wlocd 1>/dev/null 2>/dev/null
rm -rf /etc/rc3.d/K01wlocd 1>/dev/null 2>/dev/null
%post
ln -s /etc/init.d/wlocd /etc/rc5.d/S99wlocd 1>/dev/null 2>/dev/null
ln -s /etc/init.d/wlocd /etc/rc5.d/K01wlocd 1>/dev/null 2>/dev/null
ln -s /etc/init.d/wlocd /etc/rc3.d/S99wlocd 1>/dev/null 2>/dev/null
ln -s /etc/init.d/wlocd /etc/rc3.d/K01wlocd 1>/dev/null 2>/dev/null
ldconfig
/etc/init.d/wlocd start 1>/dev/null 2>/dev/null

