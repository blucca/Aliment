# End-to-end river generation test: the server runs with a superflat world whose only biome is
# minecraft:river, so every generated chunk goes through Fabric's biome modification and the
# outbreak:willow_river placed feature.

say OUTBREAK_RIVERWORLD_START

# 垂柳 strands first (removing trunks first would make the leaves decay)
execute store result score #a ob_verify run fill 0 -64 0 31 -35 31 minecraft:air replace outbreak:willow_vines
execute if score #a ob_verify matches 1.. run say OUTBREAK_RIVERWORLD_VINES_OK
execute if score #a ob_verify matches 0 run say OUTBREAK_RIVERWORLD_NO_VINES

execute store result score #b ob_verify run fill 0 -64 0 31 -35 31 minecraft:air replace outbreak:willow_log
execute if score #b ob_verify matches 1.. run say OUTBREAK_RIVERWORLD_TREES_OK
execute if score #b ob_verify matches 0 run say OUTBREAK_RIVERWORLD_NO_TREE

execute store result score #c ob_verify run fill 0 -64 0 31 -35 31 minecraft:air replace outbreak:willow_leaves
execute if score #c ob_verify matches 1.. run say OUTBREAK_RIVERWORLD_LEAVES_OK
execute if score #c ob_verify matches 0 run say OUTBREAK_RIVERWORLD_NO_LEAVES

say OUTBREAK_RIVERWORLD_DONE
scoreboard players set #run ob_verify 0
