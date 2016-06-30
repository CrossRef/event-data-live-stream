# to be run from base directory
set -e
echo "Compile JAR"
sudo -u deploy lein uberjar
echo "Install service"
cp etc/*.service /etc/systemd/system/
echo "Reload services"
systemctl daemon-reload
echo "Restart service"
service event-data-live-stream restart
echo "Done!"
