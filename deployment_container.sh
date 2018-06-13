docker service create --name gateway-service -p 8765:8765 --replicas 2 --network overnet --constraint 'node.role == manager' 127.0.0.1:5000/gateway-service
docker service create --name discovery-service -p 8761:8761 --replicas 1 --network overnet 127.0.0.1:5000/discovery-service
docker service create --name zipkin-service -p 9411:9411 --replicas 1 --network overnet 127.0.0.1:5000/zipkin-service
docker service create --name configuration-service -p 9090:9090 --replicas 1 --network overnet 127.0.0.1:5000/configuration-service
docker service create --name leave-cancel-service --replicas 1 --network overnet 127.0.0.1:5000/leave-cancel-service
docker service create --name birthday-service --replicas 1 --network overnet 127.0.0.1:5000/birthday-service
docker service create --name feedback-service --replicas 1 --network overnet 127.0.0.1:5000/feedback-service
docker service create --name leave-apply-service --replicas 1 --network overnet 127.0.0.1:5000/leave-apply-service
docker service create --name leave-history-service --replicas 1 --network overnet 127.0.0.1:5000/leave-history-service
docker service create --name regularize-history-service --replicas 1 --network overnet 127.0.0.1:5000/regularize-history-service
docker service create --name regularize-apply-service --replicas 1 --network overnet 127.0.0.1:5000/regularize-apply-service
docker service create --name attendance-service --replicas 1 --network overnet 127.0.0.1:5000/attendance-service
docker service create --name regularize-service --replicas 1 --network overnet 127.0.0.1:5000/regularize-service
docker service create --name sap-endpoint-service --replicas 2 --network overnet 127.0.0.1:5000/sap-endpoint-service
docker service create --name leave-service --replicas 1 --network overnet 127.0.0.1:5000/leave-service
docker service create --name leave-pending-approval-service --replicas 1 --network overnet 127.0.0.1:5000/leave-pending-approval-service
docker service create --name regularize-pending-approval-service --replicas 1 --network overnet 127.0.0.1:5000/regularize-pending-approval-service
docker service create --name approval-service --replicas 1 --network overnet 127.0.0.1:5000/approval-service
docker service create --name user-info-service --replicas 1 --network overnet 127.0.0.1:5000/user-info-service
docker service create --name holiday-service --replicas 1 --network overnet 127.0.0.1:5000/holiday-service
docker service create --name bookmark-service --replicas 1 --network overnet 127.0.0.1:5000/bookmark-service
docker service create --name notification-service --replicas 1 --network overnet 127.0.0.1:5000/notification-service
docker service create --name notification-queue-service --replicas 1 --network overnet 127.0.0.1:5000/notification-queue-service
docker service create --name notification-push-service --replicas 1 --network overnet 127.0.0.1:5000/notification-push-service
docker service create --name manager-info-service --replicas 1 --network overnet 127.0.0.1:5000/manager-info-service
docker service create --name notification-history-service --replicas 1 --network overnet 127.0.0.1:5000/notification-history-service
docker service create --name notification-leave-apply-service --replicas 1 --network overnet 127.0.0.1:5000/notification-leave-apply-service
docker service create --name auth-service --replicas 1 --network overnet 127.0.0.1:5000/auth-service
docker service create --name cache-store-service --replicas 1 --network overnet 127.0.0.1:5000/cache-store-service
docker service create --name medical-report-service --replicas 1 --network overnet 127.0.0.1:5000/medical-report-service
docker service create --name medical-service --replicas 1 --network overnet 127.0.0.1:5000/medical-service
docker service create --name book-medical-service --replicas 1 --network overnet 127.0.0.1:5000/book-medical-service
docker service create --name personal-info-service --replicas 1 --network overnet 127.0.0.1:5000/personal-info-service
docker service create --name form16-service --replicas 1 --network overnet 127.0.0.1:5000/form16-service
docker service create --name previous-employment-service --replicas 1 --network overnet 127.0.0.1:5000/previous-employment-service
docker service create --name user-address-service --replicas 1 --network overnet 127.0.0.1:5000/user-address-service
docker service create --name user-contact-service --replicas 1 --network overnet 127.0.0.1:5000/user-contact-service
docker service create --name house-rent-declaration-service --replicas 1 --network overnet 127.0.0.1:5000/house-rent-declaration-service
docker service create --name house-rent-receipt-service --replicas 1 --network overnet 127.0.0.1:5000/house-rent-receipt-service
docker service create --name app-service --replicas 1 --network overnet 127.0.0.1:5000/app-service
docker service create --name app-history-service --replicas 1 --network overnet 127.0.0.1:5000/app-history-service
docker service create --name house-loan-service --replicas 1 --network overnet 127.0.0.1:5000/house-loan-service