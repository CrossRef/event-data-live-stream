# to be run from base directory
sudo -u deploy lein uberjar
cp etc/*.service /etc/systemd/system/
systemctl daemon-reload