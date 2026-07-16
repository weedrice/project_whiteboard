# NoviIs production operations aliases.
# Logs may contain personal data. Avoid copying them into public/shared channels.

# Navigation
alias nv-cd-app='cd /opt/app'
alias nv-cd-logs='cd /opt/app/logs'

# Application
alias nv-app='systemctl is-active app'
alias nv-app-status='systemctl status app --no-pager -l'
alias nv-health='curl -fsS --max-time 5 http://127.0.0.1:8081/actuator/health && echo'
alias nv-metrics='curl -fsS --max-time 10 http://127.0.0.1:8081/actuator/prometheus | head -n 30'
alias nv-log='tail -n 200 /opt/app/logs/whiteboard-active.log'
alias nv-logf='tail -F /opt/app/logs/whiteboard-active.log'
alias nv-err='tail -n 200 /opt/app/logs/whiteboard-error.log'
alias nv-errf='tail -F /opt/app/logs/whiteboard-error.log'
alias nv-journal='sudo journalctl -u app -n 200 --no-pager'
alias nv-journalf='sudo journalctl -fu app'

# Nginx
alias nv-nginx='systemctl is-active nginx'
alias nv-nginx-status='systemctl status nginx --no-pager -l'
alias nv-nginx-test='sudo nginx -t'
alias nv-nginx-access='sudo tail -n 200 /var/log/nginx/access.log'
alias nv-nginx-accessf='sudo tail -F /var/log/nginx/access.log'
alias nv-nginx-error='sudo tail -n 200 /var/log/nginx/error.log'
alias nv-nginx-errorf='sudo tail -F /var/log/nginx/error.log'

# Host overview
alias nv-services='systemctl is-active app nginx prometheus grafana-server'
alias nv-disk='df -h /'
alias nv-log-size='sudo du -sh /opt/app/logs /var/log/nginx && sudo journalctl --disk-usage'
alias nv-backups='sudo ls -lah /opt/app/ops-backups'
