cd services
call sbt publishLocal
cd ..

md account\lib
md account-sync\lib
md transfer\lib

copy services\target\scala-2.12\services_2.12-0.1.jar account\lib
copy services\target\scala-2.12\services_2.12-0.1.jar account-sync\lib
copy services\target\scala-2.12\services_2.12-0.1.jar transfer\lib

cd account
call sbt docker:publishLocal
cd ..

cd account-sync
call sbt docker:publishLocal
cd ..

cd transfer
call sbt docker:publishLocal
cd ..

docker build .\api -t ledger-api:0.1