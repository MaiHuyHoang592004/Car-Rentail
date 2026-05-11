import os

# Fix Vehicle.java - change field name from 'year' to 'manufactureYear' to match DB column 'manufacture_year'
path = r'C:\Car Rentail\src\main\java\com\rentflow\vehicle\entity\Vehicle.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('@Column(name = "year", nullable = false)', '@Column(name = "manufacture_year", nullable = false)')
content = content.replace('private Integer year;', 'private Integer manufactureYear;')
content = content.replace('this.year = year;', 'this.manufactureYear = year;')
content = content.replace('return year;', 'return manufactureYear;')
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Fixed Vehicle.java')

# Fix V2__vehicle_listing.sql
path2 = r'C:\Car Rentail\src\main\resources\db\migration\V2__vehicle_listing.sql'
with open(path2, 'r', encoding='utf-8') as f:
    content2 = f.read()
content2 = content2.replace('year                        INTEGER NOT NULL', 'manufacture_year INTEGER NOT NULL')
content2 = content2.replace('chk_vehicles_year CHECK (year >= 1990)', 'chk_vehicles_manufacture_year CHECK (manufacture_year >= 1990)')
with open(path2, 'w', encoding='utf-8') as f:
    f.write(content2)
print('Fixed V2__vehicle_listing.sql')
