sh docker_image_tag.sh
echo "!!!!!!!!!! Tagging Completed !!!!!!!!!!!"
sh docker_image_push.sh
echo "!!!!!!!!!! Docker Image Push To Registry Completed !!!!!!!!!!!"
sh deployment_container.sh
echo "!!!!!!!!!! Deployment Completed !!!!!!!!!!!"