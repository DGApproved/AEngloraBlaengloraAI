# 1. Add Apache (www-data) to your personal user group
sudo usermod -aG $USER www-data

# 2. Grant your group traversal rights to your home and project folders
sudo chmod g+x /home/$USER
sudo chmod g+x /home/$USER/AI

# 3. Ensure the HTML folder and its contents are fully readable by the group
sudo chown -R $USER:$USER /home/$USER/AI/HTML
sudo chmod -R 775 /home/$USER/AI/HTML

# 4. Set dedicated home permissions
sudo chmod o+x /home/root
sudo chmod o+x /home/root/AI

# 5. Restart Apache so it registers its new group membership
sudo systemctl restart apache2