%define __jar_repack %{nil}

%global username akiban

%define relname %{name}-%{version}-%{release}

Name:           akiban-server
Version:        0.0.2
Release:        REVISION%{?dist}
Summary:        Akiban Server is the main server for the Akiban Orthogonal Architecture.

Group:          Development/Libraries
License:        GPLv2 with exceptions
URL:            http://akiban.com/

Source0: cserver.tar.gz

BuildRequires: java-devel
BuildRequires: jpackage-utils

Conflicts:     akiban-server

Requires:      java >= 1.6.0
Requires:      jpackage-utils
Requires(pre): user(akiban)
Requires(pre): group(akiban)
Requires(pre): shadow-utils
Provides:      user(akiban)
Provides:      group(akiban)

BuildArch:      noarch

%description
Akiban Server is the main server for the Akiban Orthogonal Architecture.

For more information see http://akiban.com/

%prep
%setup -q -n cserver

%build
mvn -Dmaven.test.skip=true -B clean install

%install
rm -rf ${RPM_BUILD_ROOT}
mkdir -p ${RPM_BUILD_ROOT}%{_sysconfdir}/%{username}/
mkdir -p ${RPM_BUILD_ROOT}/usr/share/%{username}
mkdir -p ${RPM_BUILD_ROOT}/usr/share/%{username}/lib
mkdir -p ${RPM_BUILD_ROOT}/etc/%{username}/config
mkdir -p ${RPM_BUILD_ROOT}/etc/rc.d/init.d/
mkdir -p ${RPM_BUILD_ROOT}/etc/security/limits.d/
mkdir -p ${RPM_BUILD_ROOT}/etc/default/
mkdir -p ${RPM_BUILD_ROOT}/usr/sbin
mkdir -p ${RPM_BUILD_ROOT}/usr/bin
cp -p redhat/cluster.properties ${RPM_BUILD_ROOT}/etc/%{username}/
cp -p redhat/chunkserver.properties ${RPM_BUILD_ROOT}/etc/%{username}/
cp -p redhat/log4j.properties ${RPM_BUILD_ROOT}/etc/%{username}/
cp -p redhat/akiban-server ${RPM_BUILD_ROOT}/etc/rc.d/init.d/
cp -p target/akiban-cserver-0.0.2-SNAPSHOT-jar-with-dependencies.jar ${RPM_BUILD_ROOT}/usr/share/%{username}/lib
cp conf/akiban-env.sh ${RPM_BUILD_ROOT}/etc/%{username}/
mv bin/akserver ${RPM_BUILD_ROOT}/usr/sbin
mkdir -p ${RPM_BUILD_ROOT}/var/lib/%{username}
mkdir -p ${RPM_BUILD_ROOT}/var/lib/%{username}
mkdir -p ${RPM_BUILD_ROOT}/var/lib/%{username}
mkdir -p ${RPM_BUILD_ROOT}/var/run/%{username}
mkdir -p ${RPM_BUILD_ROOT}/var/log/%{username}

%clean
rm -rf ${RPM_BUILD_ROOT}

%pre
getent group %{username} >/dev/null || groupadd -r %{username}
getent passwd %{username} >/dev/null || \
useradd -d /usr/share/%{username} -g %{username} -M -r %{username}
exit 0

%preun
# only delete user on removal, not upgrade
if [ "$1" = "0" ]; then
    userdel %{username}
fi

%files
%defattr(-,root,root,0755)
%attr(755,root,root) %{_sbindir}/akserver
%attr(755,root,root) /etc/rc.d/init.d/akiban-server
%attr(755,root,root) /etc/default/%{username}
%attr(755,%{username},%{username}) /usr/share/%{username}*
%attr(755,%{username},%{username}) %config(noreplace) /%{_sysconfdir}/%{username}
%attr(755,%{username},%{username}) %config(noreplace) /var/lib/%{username}/*
%attr(755,%{username},%{username}) /var/log/%{username}*
%attr(755,%{username},%{username}) /var/run/%{username}*

%post
alternatives --install /etc/%{username}/config %{username} /etc/%{username}/default.conf/ 0
exit 0

%postun
# only delete alternative on removal, not upgrade
if [ "$1" = "0" ]; then
    alternatives --remove %{username} /etc/%{username}/default.conf/
fi
exit 0
