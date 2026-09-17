set -a
source ~/Documents/nkap/.env
set -e
set +a

cd ~/Documents/nkap || exit 1

mkdir -p ~/nkap-backups
docker compose exec -T db pg_dump -U $POSTGRES_USER -d $POSTGRES_DB | gzip > ~/nkap-backups/nkap_$(date +%F_%H%M).sql.gz
find ~/nkap-backups/ -name '*sql.gz' -mtime +31 -delete
rclone delete OneDrive:nkap-backups/ --min-age 31d
rclone copy ~/nkap-backups/ OneDrive:nkap-backups/ --min-age 1m
